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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CancellationException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.DatatypeConverter;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiListener;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.docker.Docker;
import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.Container;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.SystemInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.Version;
import uk.ac.cam.cl.dtg.teaching.pottery.ContainerRetryNeededException;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;

public abstract class DockerContainer implements ContainerBackend {

  private static final Logger LOG = LoggerFactory.getLogger(DockerContainer.class);
  protected final ContainerEnvConfig config;
  protected final ScheduledExecutorService scheduler;
  protected final AtomicReference<ApiStatus> apiStatus =
      new AtomicReference<>(ApiStatus.UNINITIALISED);
  protected final AtomicLong smoothedCallTime = new AtomicLong(0);
  protected final AtomicInteger timeoutMultiplier = new AtomicInteger(1);
  // Lazy initialized - use getDockerApi to access this
  private DockerApi dockerApi;

  protected abstract Collection<String> getRunningContainers();

  public DockerContainer(ContainerEnvConfig config) throws IOException {
    this.config = config;
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    FileUtil.mkdirIfNotExists(config.getLibRoot());
    FileUtil.mkdirIfNotExists(config.getTempRoot());
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

  @Override
  public void stop() {
    LOG.info("Shutting down scheduler");
    for (Runnable r : scheduler.shutdownNow()) {
      r.run();
    }
    LOG.info("Killing remaining containers");
    try {
      DockerApi docker = getDockerApi();
      for (String containerId : getRunningContainers()) {
        DockerUtil.killContainer(containerId, docker);
      }
      docker.close();
    } catch (ApiUnavailableException e) {
      LOG.error("Unable to remove running containers, API unavailable", e);
    }
  }

  synchronized DockerApi getDockerApi() throws ApiUnavailableException {
    if (dockerApi == null) {
      dockerApi = initializeDockerApi();
    }
    return dockerApi;
  }

  private synchronized DockerApi initializeDockerApi() throws ApiUnavailableException {
    DockerApi docker = new Docker("localhost", 2375, 1).api(new ApiPerformanceListener());
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

  @Override
  public ApiStatus getApiStatus() {
    return apiStatus.get();
  }

  @Override
  public long getSmoothedCallTime() {
    return smoothedCallTime.get();
  }

  protected ScheduledFuture<Boolean> scheduleTimeoutKiller(
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

  @Override
  public void setTimeoutMultiplier(int multiplier) {
    timeoutMultiplier.set(multiplier);
  }

  @Override
  public ContainerExecResponse executeContainer(ExecutionConfig executionConfig)
      throws ApiUnavailableException, ContainerExecutionException {
    for (int i = 0; i < 5; ++i) {
      try {
        return executeContainerInner(executionConfig);
      } catch (ContainerExecutionException e) {
        if (e.getMessage().contains("container process is already dead")) {
          LOG.warn("Caught Docker race condition in container start - will retry");
        } else {
          throw e;
        }
      } catch (ContainerRetryNeededException e) {
        LOG.warn("Detected container retry required - will retry");
      }
    }
    throw new ContainerExecutionException("Failed to execute container", "");
  }

  protected abstract ContainerExecResponse executeContainerInner(ExecutionConfig executionConfig)
      throws ApiUnavailableException, ContainerExecutionException, ContainerRetryNeededException;

  protected ContainerExecResponse startContainer(
      ExecutionConfig executionConfig, String containerName, String containerId, DockerApi docker)
      throws ApiUnavailableException, ContainerRetryNeededException, ContainerExecutionException {
    long startTime = System.currentTimeMillis();
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
      ContainerExecResponse.Status status = ContainerExecResponse.Status.FAILED_UNKNOWN;
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
            status = ContainerExecResponse.Status.COMPLETED;
          } else {
            status = ContainerExecResponse.Status.FAILED_EXITCODE;
          }
        }
      }

      // Check to make sure its really stopped the container
      if (!knownStopped) {
        containerInfo = docker.inspectContainer(containerId, false);
        if (containerInfo.getState().getRunning()) {
          LOG.info(
              "Container {} still running despite receiving a close from it - killing",
              containerId);
          DockerUtil.killContainer(containerId, docker);
          containerInfo = docker.inspectContainer(containerId, false);
        }
        if (containerInfo.getState().getExitCode() == 0) {
          status = ContainerExecResponse.Status.COMPLETED;
        } else {
          status = ContainerExecResponse.Status.FAILED_EXITCODE;
        }
      }

      if (containerInfo.getState().getOomKilled()) {
        status = ContainerExecResponse.Status.FAILED_OOM;
      }

      timeoutKiller.cancel(false);
      try {
        if (timeoutKiller.get()) {
          status = ContainerExecResponse.Status.FAILED_TIMEOUT;
        }
      } catch (InterruptedException | ExecutionException | CancellationException e) {
        // ignore
      }

      if (diskUsageKiller.isKilled()) {
        status = ContainerExecResponse.Status.FAILED_DISK;
      }

      if (attachListener.hasOverflowed()) {
        status = ContainerExecResponse.Status.FAILED_OUTPUT;
      }

      try {
        session.get(5, TimeUnit.SECONDS).close();
      } catch (InterruptedException e) {
        // ignore
      } catch (ExecutionException e) {
        DockerContainerImpl.LOG.error(
            "An exception occurred collecting the websocket session from the future", e.getCause());
      } catch (TimeoutException e) {
        DockerContainerImpl.LOG.error(
            "Time occurred collecting the websocket session from the future", e);
      }

      String recordedResponse = attachListener.getOutput();

      LOG.debug("Container response: {}", recordedResponse);

      Pattern p = Pattern.compile("^([a-z0-9]+)  -\n\\z", Pattern.MULTILINE);
      if (status == ContainerExecResponse.Status.COMPLETED
          || status == ContainerExecResponse.Status.FAILED_EXITCODE) {
        Matcher matcher = p.matcher(recordedResponse);
        if (!matcher.find()) {
          LOG.warn("Response {} failed to match", recordedResponse);
          throw new ContainerRetryNeededException();
        }
        String checksum = matcher.group(1);
        LOG.debug("Extracted checksum {}", checksum);
        recordedResponse = matcher.replaceAll("");
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(recordedResponse.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md.digest();
        String myHash = DatatypeConverter.printHexBinary(digest).toLowerCase();
        if (!checksum.equals(myHash)) {
          LOG.warn("The checksum for response {} is not equal to {}", recordedResponse, checksum);
          throw new ContainerRetryNeededException();
        }
      }
      return ContainerExecResponse.create(
          status,
          attachListener.getOutput(),
          System.currentTimeMillis() - startTime,
          executionConfig.taint(),
          containerName);
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Error executing container", e);
      throw new ContainerExecutionException(
          "Failed to load MD5 digest algorithm when executing container: %s", containerName);
    } finally {
      diskUsageKillerFuture.cancel(false);
    }
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
