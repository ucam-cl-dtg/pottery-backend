/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.docker.DockerPatch;
import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse.Status;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;

/** Docker implementation of container backend. */
public class DockerContainerImpl extends DockerContainer implements ContainerBackend {
  protected static final Logger LOG = LoggerFactory.getLogger(DockerContainerImpl.class);

  private final AtomicInteger containerNameCounter = new AtomicInteger(0);
  private final AtomicInteger timeoutMultiplier = new AtomicInteger(1);

  private final ConcurrentSkipListSet<String> runningContainers = new ConcurrentSkipListSet<>();

  @Inject
  public DockerContainerImpl(ContainerEnvConfig config) throws IOException {
    super(config);
  }

  @Override
  protected Collection<String> getRunningContainers() {
    return runningContainers;
  }

  @Override
  public synchronized String getVersion() throws ApiUnavailableException {
    return "Docker:" + getDockerApi().getVersion().getApiVersion();
  }

  @Override
  public void setTimeoutMultiplier(int multiplier) {
    timeoutMultiplier.set(multiplier);
  }

  @Override
  public String getInternalMountPath() {
    return "/mnt/pottery";
  }

  @Override
  public ContainerExecResponse executeContainer(ExecutionConfig executionConfig)
      throws ApiUnavailableException, ContainerExecutionException {

    // Make sure all the mount points exist, otherwise docker creates them for us with the wrong
    // file permissions
    for (PathSpecification specification : executionConfig.pathSpecification()) {
      try {
        FileUtil.mkdirIfNotExists(specification.host());
      } catch (IOException e) {
        throw new ContainerExecutionException(
            "Failed to create mount point " + specification.host(), e);
      }
    }

    String containerName =
        this.config.getContainerPrefix() + containerNameCounter.incrementAndGet();
    LOG.debug("Creating container {}", containerName);

    DockerApi docker = getDockerApi();

    long startTime = System.currentTimeMillis();
    try {
      ContainerConfig config = executionConfig.toContainerConfig();
      ContainerResponse response = docker.createContainer(containerName, config);
      final String containerId = response.getId();
      getRunningContainers().add(containerId);
      try {
        docker.startContainer(containerId);
        AttachListener attachListener =
            new AttachListener(
                executionConfig.containerRestrictions().getOutputLimitKilochars() * 1000);

        ScheduledFuture<Boolean> timeoutKiller =
            scheduleTimeoutKiller(
                executionConfig.containerRestrictions().getTimeoutSec() * timeoutMultiplier.get(),
                containerId,
                attachListener);

        DiskUsageKiller diskUsageKiller =
            new DiskUsageKiller(
                containerId,
                this,
                executionConfig.containerRestrictions().getDiskWriteLimitMegabytes() * 1024 * 1024,
                attachListener);
        ScheduledFuture<?> diskUsageKillerFuture =
            scheduler.scheduleAtFixedRate(diskUsageKiller, 10L, 10L, TimeUnit.SECONDS);

        try {
          Future<Session> session =
              docker.attach(containerId, true, true, true, true, true, attachListener);

          // Wait for container to finish (or be killed)
          Status status = Status.FAILED_UNKNOWN;
          boolean knownStopped = false;
          boolean closed = false;
          ContainerInfo containerInfo = null;
          while (!closed) {
            closed = attachListener.waitForClose(60 * 1000);
            containerInfo = docker.inspectContainer(containerId, false);
            if (!containerInfo.getState().getRunning()) {
              knownStopped = true;
              closed = true;
              if (containerInfo.getState().getExitCode() == 0) {
                status = Status.COMPLETED;
              } else {
                status = Status.FAILED_EXITCODE;
              }
            }
          }

          // Check to make sure its really stopped the container
          if (!knownStopped) {
            containerInfo = docker.inspectContainer(containerId, false);
            if (containerInfo.getState().getRunning()) {
              DockerUtil.killContainer(containerId, docker);
              containerInfo = docker.inspectContainer(containerId, false);
            }
            if (containerInfo.getState().getExitCode() == 0) {
              status = Status.COMPLETED;
            } else {
              status = Status.FAILED_EXITCODE;
            }
          }

          if (containerInfo.getState().getOomKilled()) {
            status = Status.FAILED_OOM;
          }

          timeoutKiller.cancel(false);
          try {
            if (timeoutKiller.get()) {
              status = Status.FAILED_TIMEOUT;
            }
          } catch (InterruptedException | ExecutionException | CancellationException e) {
            // ignore
          }

          if (diskUsageKiller.isKilled()) {
            status = Status.FAILED_DISK;
          }

          if (attachListener.hasOverflowed()) {
            status = Status.FAILED_OUTPUT;
          }

          try {
            session.get(5, TimeUnit.SECONDS).close();
          } catch (InterruptedException e) {
            // ignore
          } catch (ExecutionException e) {
            LOG.error(
                "An exception occurred collecting the websocket session from the future",
                e.getCause());
          } catch (TimeoutException e) {
            LOG.error("Time occurred collecting the websocket session from the future", e);
          }

          LOG.debug("Container response: {}", attachListener.getOutput());
          return ContainerExecResponse.create(
              status,
              attachListener.getOutput(),
              System.currentTimeMillis() - startTime,
              executionConfig.taint());
        } finally {
          diskUsageKillerFuture.cancel(false);
        }
      } finally {
        getRunningContainers().remove(containerId);
        DockerPatch.deleteContainer(docker, containerId, true, true);
      }
    } catch (RuntimeException e) {
      LOG.error("Error executing container", e);
      throw new ContainerExecutionException(
          String.format(
              "An error (%s) occurred when executing container: %s",
              e.getClass().getName(), e.getMessage()));
    }
  }
}
