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

import autovalue.shaded.com.google.common.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiListener;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.docker.Docker;
import uk.ac.cam.cl.dtg.teaching.docker.DockerPatch;
import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.Container;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerResponse;
import uk.ac.cam.cl.dtg.teaching.docker.model.SystemInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.Version;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse.Status;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;

/** Docker implementation of container backend. */
public class DockerContainerImpl implements ContainerBackend {

  protected static final Logger LOG = LoggerFactory.getLogger(DockerContainerImpl.class);

  private final ContainerEnvConfig config;

  private final ScheduledExecutorService scheduler;

  // Lazy initialized - use getDockerApi to access this
  private DockerApi dockerApi;

  private final ConcurrentSkipListSet<String> runningContainers = new ConcurrentSkipListSet<>();

  private final AtomicInteger containerNameCounter = new AtomicInteger(0);
  private final AtomicInteger timeoutMultiplier = new AtomicInteger(1);
  private final AtomicReference<ApiStatus> apiStatus =
      new AtomicReference<>(ApiStatus.UNINITIALISED);
  private final AtomicLong smoothedCallTime = new AtomicLong(0);

  @Inject
  public DockerContainerImpl(ContainerEnvConfig config) throws IOException {
    this.config = config;
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    FileUtil.mkdirIfNotExists(config.getLibRoot());
    FileUtil.mkdirIfNotExists(config.getTempRoot());
  }

  @Override
  public void stop() {
    LOG.info("Shutting down scheduler");
    for (Runnable r : scheduler.shutdownNow()) {
      r.run();
    }
    LOG.info("Killing remaining containers");
    try {
      DockerApi docker = getDockerApi();
      for (String containerId : runningContainers) {
        DockerUtil.killContainer(containerId, docker);
      }
      docker.close();
    } catch (ApiUnavailableException e) {
      LOG.error("Unable to remove running containers, API unavailable", e);
    }
  }

  @Override
  public ApiStatus getApiStatus() {
    return apiStatus.get();
  }

  @Override
  public long getSmoothedCallTime() {
    return smoothedCallTime.get();
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

  private final Set<String> seenIds = Sets.newSetFromMap(new ConcurrentHashMap<>());

  @Override
  public ContainerExecResponse executeContainer(ExecutionConfig executionConfig)
      throws ApiUnavailableException, ContainerExecutionException {

    String containerName =
        this.config.getContainerPrefix() + containerNameCounter.incrementAndGet();
    LOG.debug("Creating container {}", containerName);

    DockerApi docker = getDockerApi();

    long startTime = System.currentTimeMillis();
    try {
      ContainerConfig config = executionConfig.toContainerConfig();
      ContainerResponse response = docker.createContainer(containerName, config);
      final String containerId = response.getId();
      if (seenIds.contains(containerId)) {
          LOG.error("REUSED DOCKER CONTAINER ID");
          throw new Error("REUSED DOCKER CONTAINER ID");
      }
      seenIds.add(containerId);
      runningContainers.add(containerId);
      try {
        retryStartContainer(containerId, docker);

        AttachListener attachListener = new AttachListener();

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
      } finally {
        runningContainers.remove(containerId);
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

  private ScheduledFuture<Boolean> scheduleTimeoutKiller(
      long timeoutSec, String containerId, AttachListener attachListener) {
    if (timeoutSec <= 0) {
      return new EmptyScheduledFuture<>();
    }
    return scheduler.schedule(
        () -> {
          try {
            DockerUtil.killContainer(containerId, getDockerApi());
            attachListener.notifyClose();
            return true;
          } catch (RuntimeException e) {
            LOG.error("Caught exception killing container", e);
            return false;
          }
        },
        timeoutSec,
        TimeUnit.SECONDS);
  }

  synchronized DockerApi getDockerApi() throws ApiUnavailableException {
    if (dockerApi == null) {
      dockerApi = initializeDockerApi();
    }
    return dockerApi;
  }

  private synchronized DockerApi initializeDockerApi() throws ApiUnavailableException {
    DockerApi docker = new Docker("localhost", 2375, 100).api(new ApiPerformanceListener());
    if (LOG.isInfoEnabled()) {
      Version v = docker.getVersion();
      LOG.info("Connected to docker, API version: {}", v.getApiVersion());
    }
    SystemInfo info = docker.systemInfo();
    if (info.getSwapLimit() == null || !info.getSwapLimit()) {
      LOG.warn(
          "WARNING: swap limits are disabled for this kernel. Add \"cgroup_enable=memory "
              + "swapaccount=1\" to your kernel command line");
    }
    deleteOldContainers(config.getContainerPrefix(), docker);
    apiStatus.set(ApiStatus.OK);
    return docker;
  }

  private static void deleteOldContainers(String containerPrefix, DockerApi docker)
      throws ApiUnavailableException {
    for (Container i : docker.listContainers(true, null, null, null, null)) {
      Optional<String> matchedName = getPotteryTransientName(containerPrefix, i);
      if (matchedName.isPresent()) {
        LOG.warn("Deleting old container named {}", matchedName.get());
        try {
          docker.deleteContainer(i.getId(), true, true);
        } catch (RuntimeException e) {
          LOG.error("Error deleting old container", e);
        }
      }
    }
  }

  private static Optional<String> getPotteryTransientName(String containerPrefix, Container i) {
    final String prefix = "/" + containerPrefix;
    return Arrays.stream(i.getNames()).filter(name -> name.startsWith(prefix)).findAny();
  }

  private static boolean startContainer(String containerId, DockerApi docker)
      throws ApiUnavailableException {
    try {
      docker.startContainer(containerId);
      return true;
    } catch (RuntimeException e) {
      if (e.getMessage().contains("container process is already dead")) {
        LOG.warn("Caught Docker race condition in container start - will retry");
        return false;
      }
      throw e;
    }
  }

  private static void retryStartContainer(String containerId, DockerApi docker)
      throws ApiUnavailableException {
    for (int i = 0; i < 5; ++i) {
      if (startContainer(containerId, docker)) {
        return;
      }
    }
    throw new RuntimeException("Failed to start docker container after 5 retries");
  }

    private class ApiPerformanceListener implements ApiListener {
    @Override
    public void callCompleted(boolean apiAvailable, long timeTaken, String methodName) {
      long callTime = smoothedCallTime.get();
      callTime = (timeTaken >> 3) + callTime - (callTime >> 3);
      smoothedCallTime.set(callTime);
      if (!apiAvailable) {
        apiStatus.set(ApiStatus.FAILED);
      } else if (callTime > 1000) {
        apiStatus.set(ApiStatus.SLOW_RESPONSE_TIME);
      } else {
        apiStatus.set(ApiStatus.OK);
      }
    }
  }
}
