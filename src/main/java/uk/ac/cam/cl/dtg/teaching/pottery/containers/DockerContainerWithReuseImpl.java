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
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
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

/** Docker implementation of container backend which reuses containers. */
public class DockerContainerWithReuseImpl extends DockerContainer implements ContainerBackend {
  protected static final Logger LOG = LoggerFactory.getLogger(DockerContainerWithReuseImpl.class);

  private final AtomicInteger containerNameCounter = new AtomicInteger(0);

  private static final String POTTERY_EXECUTE = "__pottery_execute";

  private static class ContainerStatus {
    /** The repo id if user-controlled code run on this container. */
    @Nullable String repoIdIfSet;

    /** The container is currently being used for an execution. */
    boolean inUse;

    /** The name of the container. */
    String containerName;
  }

  private static class LockableMap {
    final Object lock = new Object();
    final Map<String, ContainerStatus> map = new HashMap<>();
  }

  private final ConcurrentSkipListMap<ContainerSettings, LockableMap> containers =
      new ConcurrentSkipListMap<>();

  private final AtomicInteger timeoutMultiplier = new AtomicInteger(1);

  @Inject
  public DockerContainerWithReuseImpl(ContainerEnvConfig config) throws IOException {
    super(config);
  }

  @Override
  protected Collection<String> getRunningContainers() {
    return containers.values().stream()
        .map(ci -> ci.map)
        .flatMap(cm -> cm.keySet().stream())
        .collect(Collectors.toList());
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

    // Once we find a container that is suitable we'll put its ID here (we'll create one if needed.)
    String containerId = null;

    String containerName = null;
    Status status = Status.FAILED_UNKNOWN;

    File hostRw = null;
    File hostRo = null;

    ContainerSettings containerSettings = ContainerSettings.create(executionConfig);

    LockableMap possibleContainers =
        containers.computeIfAbsent(containerSettings, cs -> new LockableMap());

    try {
      synchronized (possibleContainers.lock) {
        // If we find a container that is suitable and untainted, we'll store it's ID here.
        String untaintedContainerId = null;

        for (Map.Entry<String, ContainerStatus> entry : possibleContainers.map.entrySet()) {
          ContainerStatus containerStatus = entry.getValue();
          if (containerStatus.inUse) {
            continue;
          }
          if (Objects.equals(containerStatus.repoIdIfSet, executionConfig.taint().identity())) {
            // This container matches the taint we're looking for, so we'll use it.
            containerId = entry.getKey();
            LOG.info("Using existing container {}", containerStatus.containerName);
            break;
          }
          if (containerStatus.repoIdIfSet == null) {
            // We could taint this container if we don't find a suitable one above.
            untaintedContainerId = entry.getKey();
          }
        }
        if (containerId == null) {
          // Is there an untainted container we can taint?
          if (untaintedContainerId != null) {
            LOG.info(
                "Tainting existing container {} with {}",
                untaintedContainerId,
                executionConfig.taint().identity());
            containerId = untaintedContainerId;
          } else {
            // Make a new container
            containerName =
                (this.config.getContainerPrefix()
                        + executionConfig.imageName()
                        + "-"
                        + executionConfig.configurationHash()
                        + "-"
                        + containerNameCounter.incrementAndGet())
                    .replaceAll("[^a-zA-Z0-9_.-]", "-");
          }
        }

        if (containerId != null) {
          ContainerStatus containerStatus = possibleContainers.map.get(containerId);
          containerStatus.inUse = true;
          containerStatus.repoIdIfSet = executionConfig.taint().identity(); // Apply the taint
          containerName = containerStatus.containerName;
        }
      }

      if (containerId != null) {
        // Check container is not running
        docker.waitContainer(containerId);
      }

      File host = new File(config.getTempRoot() + "/" + containerName);

      hostRw = new File(host, "rw");
      FileUtil.mkdirIfNotExists(hostRw);
      hostRo = new File(host, "ro");
      FileUtil.mkdirIfNotExists(hostRo);

      if (containerId == null) {
        LOG.info("Creating container {}", containerName);

        // Prepare the executionConfig for swizzle
        ExecutionConfig swizzledConfig =
            executionConfig
                .toBuilder()
                .setCommand(ImmutableList.of(getInternalMountPath() + "/ro/" + POTTERY_EXECUTE))
                .setPathSpecification(
                    ImmutableList.of(
                        PathSpecification.create(hostRw, getInternalMountPath() + "/rw", true),
                        PathSpecification.create(hostRo, getInternalMountPath() + "/ro", false)))
                .build();

        ContainerConfig config = swizzledConfig.toContainerConfig();
        ContainerResponse response = docker.createContainer(containerName, config);
        containerId = response.getId();
        ContainerStatus newContainerStatus = new ContainerStatus();
        newContainerStatus.inUse = true;
        newContainerStatus.repoIdIfSet = executionConfig.taint().identity();
        newContainerStatus.containerName = containerName;

        synchronized (possibleContainers.lock) {
          possibleContainers.map.put(containerId, newContainerStatus);
        }
      }

      /// Swizzle directories

      String command = Joiner.on(" ").join(executionConfig.command());
      // Map paths
      for (PathSpecification pathSpecification : executionConfig.pathSpecification()) {
        File hostBase = pathSpecification.readWrite() ? hostRw : hostRo;
        String name =
            pathSpecification.container().getPath().replace(getInternalMountPath() + "/", "");
        String internalPath =
            getInternalMountPath()
                + "/"
                + (pathSpecification.readWrite() ? "rw" : "ro")
                + "/"
                + name;

        File hostTarget = new File(hostBase, name);
        // Map from host to swizzle area
        // We can't use symbolic links because Docker won't traverse them (probably for security).
        // We can't hard link directories because that isn't legal in the filesystem.
        // Even if we use something link cp -al to make linked files, we want changed files to be
        // available.
        // So, we'll copy read-only files and move the read-write files and then move them back
        // again at the end;
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
      java.nio.file.Files.setPosixFilePermissions(
          hostExecutable.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"));

      long startTime = System.currentTimeMillis();

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
            docker.attach(containerId, false, true, true, true, true, attachListener);

        docker.startContainer(containerId);

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
            status,
            attachListener.getOutput(),
            System.currentTimeMillis() - startTime,
            executionConfig.taint());
      } finally {
        diskUsageKillerFuture.cancel(false);
      }
    } catch (IOException | RuntimeException e) {
      destroyContainer(docker, possibleContainers, containerId);
      LOG.error("Error executing container", e);
      throw new ContainerExecutionException(
          String.format(
              "An error (%s) occurred when executing container: %s",
              e.getClass().getName(), e.getMessage()));
    } finally {
      try {
        // Copy files out and delete copies
        File host = new File(config.getTempRoot() + "/" + containerName);

        if ((status != Status.COMPLETED && status != Status.FAILED_EXITCODE)
            || hostRw == null
            || hostRo == null) {
          // Something bad happened to the container, so kill it
          destroyContainer(docker, possibleContainers, containerId);
        } else {
          // Copy files out
          for (PathSpecification pathSpecification : executionConfig.pathSpecification()) {
            File hostBase = pathSpecification.readWrite() ? hostRw : hostRo;
            String name =
                pathSpecification.container().getPath().replace(getInternalMountPath() + "/", "");

            File hostTarget = new File(hostBase, name);
            if (pathSpecification.readWrite()) {
              Files.move(hostTarget, pathSpecification.host());
            }
          }

          // Delete temporary directory
          FileUtils.deleteDirectory(host);
        }
      } catch (IOException e) {
        throw new ApiUnavailableException("Couldn't copy back files", e);
      } finally {
        // Unlock the container
        synchronized (possibleContainers.lock) {
          possibleContainers.map.compute(
              containerId,
              (k, containerStatus) -> {
                if (containerStatus != null) {
                  containerStatus.inUse = false;
                }
                return containerStatus;
              });
        }
      }
    }
  }

  private void destroyContainer(DockerApi docker, LockableMap containers, String containerId)
      throws ApiUnavailableException {
    if (containerId != null) {
      DockerPatch.deleteContainer(docker, containerId, true, true);
    }

    ContainerStatus containerStatus;
    synchronized (containers.lock) {
      containerStatus = containers.map.remove(containerId);
    }
    if (containerStatus != null) {
      File host = new File(config.getTempRoot() + "/" + containerStatus.containerName);
      try {
        FileUtil.deleteRecursive(host);
      } catch (IOException e) {
        throw new ApiUnavailableException("Couldn't delete container host directory", e);
      }
    }
  }
}
