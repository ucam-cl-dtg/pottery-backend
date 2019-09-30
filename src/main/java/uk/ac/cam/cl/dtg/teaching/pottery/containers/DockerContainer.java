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
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiListener;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.docker.Docker;
import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.Container;
import uk.ac.cam.cl.dtg.teaching.docker.model.SystemInfo;
import uk.ac.cam.cl.dtg.teaching.docker.model.Version;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;

public abstract class DockerContainer implements ContainerBackend {

  protected static final Logger LOG = LoggerFactory.getLogger(DockerContainer.class);
  protected final ContainerEnvConfig config;
  protected final ScheduledExecutorService scheduler;
  protected final AtomicReference<ApiStatus> apiStatus =
      new AtomicReference<>(ApiStatus.UNINITIALISED);
  protected final AtomicLong smoothedCallTime = new AtomicLong(0);
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
