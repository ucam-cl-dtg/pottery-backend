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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.inject.Inject;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Docker implementation of container backend which reuses containers. */
public class DockerContainerWithReuseImpl extends DockerContainer implements ContainerBackend {
  protected static final Logger LOG = LoggerFactory.getLogger(DockerContainerWithReuseImpl.class);

  private static final String POTTERY_EXECUTE = "__pottery_execute";

  private final ConcurrentSkipListMap<String, String> containers = new ConcurrentSkipListMap<>();

  private final AtomicInteger timeoutMultiplier = new AtomicInteger(1);

  @Inject
  public DockerContainerWithReuseImpl(ContainerEnvConfig config) throws IOException {
    super(config);
  }

  @Override
  protected Collection<String> getRunningContainers() {
    return containers.values();
  }

  @Override
  public synchronized String getVersion() throws ApiUnavailableException {
    return "DockerWithReuse:" + getDockerApi().getVersion().getApiVersion();
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

    DockerApi docker = getDockerApi();

    // First, find if there is an available container
    String containerName = (this.config.getContainerPrefix()
        + executionConfig.imageName()
        + "-" + executionConfig.potteryRepoId()
        + "-" + executionConfig.settingsHash()).replaceAll("[^a-zA-Z0-9_.-]", "-");

    String containerId = null;
    Status status = Status.FAILED_UNKNOWN;

    try {
      File host = new File(config.getTempRoot() + "/" + containerName);

      // Prepare the executionConfig for swizzle
      File hostRw = new File(host, "rw");
      FileUtil.mkdirIfNotExists(hostRw);
      File hostRo = new File(host, "ro");
      FileUtil.mkdirIfNotExists(hostRo);

      if (containers.containsKey(containerName)) {
        LOG.debug("Using existing container {}", containerName);
        containerId = containers.get(containerName);
        // Check container is not running
        docker.waitContainer(containerId);
        LOG.debug("Existing container wait over");
      } else {
        LOG.debug("Creating container {}", containerName);

        ExecutionConfig swizzledConfig = executionConfig.toBuilder()
            .setCommand(ImmutableList.of(getInternalMountPath() + "/ro/" + POTTERY_EXECUTE))
            .setPathSpecification(ImmutableList.of(
                PathSpecification.create(hostRw, getInternalMountPath() + "/rw", true),
                PathSpecification.create(hostRo, getInternalMountPath() + "/ro", false)
            ))
            .build();

        ContainerConfig config = swizzledConfig.toContainerConfig();
        ContainerResponse response = docker.createContainer(containerName, config);
        containerId = response.getId();
        containers.put(containerName, containerId);
      }

      /// Swizzle directories

      String command = Joiner.on(" ").join(executionConfig.command());
      // Map paths
      for (PathSpecification pathSpecification : executionConfig.pathSpecification()) {
        File hostBase = pathSpecification.readWrite() ? hostRw : hostRo;
        String name = pathSpecification.container().getPath().replace(getInternalMountPath() + "/", "");
        String internalPath = getInternalMountPath() + "/" + (pathSpecification.readWrite() ? "rw" : "ro") + "/" + name;

        File hostTarget = new File(hostBase, name);
        // Map from host to swizzle area
        // We can't use symbolic links because Docker won't traverse them (probably for security).
        // We can't hard link directories because that isn't legal in the filesystem.
        // Even if we use something link cp -al to make linked files, we want changed files to be available.
        // So, we'll copy read-only files and move the read-write files and then move them back again at the end;
        // if we are modifying these files we assume a lock is held on them already.
        // Also, we need to preserve permissions; copyFilesRecursively and move both do this.
        if (pathSpecification.readWrite()) {
          Files.move(pathSpecification.host(), hostTarget);
        } else {
          FileUtil.copyFilesRecursively(pathSpecification.host(), hostTarget);
        }

        // Map from swizzle area to inside docker
        command = command.replace(pathSpecification.container().getPath(), internalPath);
      }

      // Write command
      File hostExecutable = new File(hostRo, POTTERY_EXECUTE);
      Files.asCharSink(hostExecutable, Charset.defaultCharset()).write("#!/bin/bash\n" + command);
      java.nio.file.Files.setPosixFilePermissions(hostExecutable.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"));

      long startTime = System.currentTimeMillis();

      docker.startContainer(containerId);
      AttachListener attachListener = new AttachListener(
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
            status, attachListener.getOutput(), System.currentTimeMillis() - startTime);
      } finally {
        diskUsageKillerFuture.cancel(false);
      }
    } catch (IOException|RuntimeException e) {
      destroyContainer(docker, containerName);
      LOG.error("Error executing container", e);
      throw new ContainerExecutionException(
          String.format(
              "An error (%s) occurred when executing container: %s",
              e.getClass().getName(), e.getMessage()));
    } finally {
      if (status != Status.COMPLETED && status != Status.FAILED_EXITCODE) {
        // Something bad happened to the container, so kill it
        destroyContainer(docker, containerName);
      } else {
        // Copy files out
        File host = new File(config.getTempRoot() + "/" + containerName);

        File hostRw = new File(host, "rw");

        for (PathSpecification pathSpecification : executionConfig.pathSpecification()) {
          if (!pathSpecification.readWrite()) continue;
          File hostBase = hostRw;
          String name = pathSpecification.container().getPath().replace(getInternalMountPath() + "/", "");

          File hostTarget = new File(hostBase, name);
          try {
            if (pathSpecification.readWrite()) {
              Files.move(hostTarget, pathSpecification.host());
            }
          } catch (IOException e) {
            throw new ApiUnavailableException("Couldn't copy back files", e);
          }
        }

      }
    }
  }

  private void destroyContainer(DockerApi docker, String containerName) throws ApiUnavailableException {
    String containerId = containers.remove(containerName);
    File host = new File(config.getTempRoot() + "/" + containerName);
    if (containerId != null) {
      DockerPatch.deleteContainer(docker, containerId, true, true);
    }
    try {
      FileUtil.deleteRecursive(host);
    } catch (IOException e) {
      throw new ApiUnavailableException("Couldn't delete container host directory", e);
    }
  }
}
