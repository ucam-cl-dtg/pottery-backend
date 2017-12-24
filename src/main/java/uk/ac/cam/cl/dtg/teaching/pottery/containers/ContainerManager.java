/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015 Andrew Rice (acr31@cam.ac.uk)
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.ContainerRestrictions;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessPart;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Measurement;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.ValidatorResponse;

@Singleton
public class ContainerManager implements Stoppable {

  protected static final Logger LOG = LoggerFactory.getLogger(ContainerManager.class);

  private enum ApiStatus {
    OK,
    UNINITIALISED,
    FAILED,
    SLOW_RESPONSE_TIME
  }

  private ContainerEnvConfig config;

  // Lazy initialized - use getDockerApi to access this
  private DockerApi dockerApi;

  private ScheduledExecutorService scheduler;

  private ConcurrentSkipListSet<String> runningContainers = new ConcurrentSkipListSet<>();

  private AtomicReference<ApiStatus> apiStatus = new AtomicReference<>(ApiStatus.UNINITIALISED);
  private AtomicLong smoothedCallTime = new AtomicLong(0);
  private AtomicInteger containerNameCounter = new AtomicInteger(0);
  private AtomicInteger timeoutMultiplier = new AtomicInteger(1);
  private AtomicInteger tempDirCounter = new AtomicInteger(0);

  /**
   * Construct a new container manager and worker pool. The connection to the container backend is
   * created lazily as needed.
   */
  @Inject
  public ContainerManager(ContainerEnvConfig config) throws IOException {
    this.config = config;
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    FileUtil.mkdirIfNotExists(config.getLibRoot());
    FileUtil.mkdirIfNotExists(config.getTempRoot());
  }

  public String getApiStatus() {
    return apiStatus.get().name();
  }

  public long getSmoothedCallTime() {
    return smoothedCallTime.get();
  }

  public synchronized String getDockerVersion() throws ApiUnavailableException {
    return getDockerApi().getVersion().getApiVersion();
  }

  /**
   * Scale factor to apply to container timeouts given in container restrictions. If you are running
   * with more worker threads than cores then you might find that containers are timing out because
   * of contention. Use this method to increase all timeouts.
   */
  public void setTimeoutMultiplier(int multiplier) {
    timeoutMultiplier.set(multiplier);
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

  /**
   * Compile the task and get the response. This is done by executing the compile-test.sh script in
   * the task repo.
   */
  public ContainerExecResponse<String> execTaskCompilation(
      File taskDirHost, String imageName, ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    try {
      return executeContainer(
          ExecutionConfig.builder()
              .addPathSpecification(PathSpecification.create(taskDirHost, "/task", true))
              .addCommand("/task/compile-test.sh")
              .addCommand("/task/test")
              .addCommand("/task/harness")
              .addCommand("/task/validator")
              .setImageName(imageName)
              .setContainerRestrictions(restrictions)
              .setLocalUserId(config.getUid())
              .build(),
          Function.identity());
    } catch (ContainerExecutionException e) {
      return new ContainerExecResponse<>(false, e.getMessage(), e.getMessage(), -1);
    }
  }

  /**
   * Compile the submission and get the response. This is done by executing
   * compile/compile-solution.sh.
   */
  public ContainerExecResponse<String> execCompilation(
      File codeDirHost,
      File compilationRecipeDirHost,
      String imageName,
      ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    try {
      return executeContainer(
          ExecutionConfig.builder()
              .addPathSpecification(PathSpecification.create(codeDirHost, "/code", true))
              .addPathSpecification(
                  PathSpecification.create(compilationRecipeDirHost, "/compile", false))
              .addPathSpecification(
                  PathSpecification.create(config.getLibRoot(), "/testlib", false))
              .addCommand("/compile/compile-solution.sh")
              .addCommand("/code")
              .addCommand("/testlib")
              .setImageName(imageName)
              .setContainerRestrictions(restrictions)
              .setLocalUserId(config.getUid())
              .build(),
          Function.identity());
    } catch (ContainerExecutionException e) {
      return new ContainerExecResponse<>(false, e.getMessage(), e.getMessage(), -1);
    }
  }

  /** Execute the test harness by running harness/run-harness.sh. */
  public ContainerExecResponse<HarnessResponse> execHarness(
      File codeDirHost,
      File harnessRecipeDirHost,
      String imageName,
      ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    try {
      return executeContainer(
          ExecutionConfig.builder()
              .addPathSpecification(PathSpecification.create(codeDirHost, "/code", true))
              .addPathSpecification(
                  PathSpecification.create(harnessRecipeDirHost, "/harness", false))
              .addPathSpecification(
                  PathSpecification.create(config.getLibRoot(), "/testlib", false))
              .addCommand("/harness/run-harness.sh")
              .addCommand("/code")
              .addCommand("/harness")
              .addCommand("/testlib")
              .setImageName(imageName)
              .setContainerRestrictions(restrictions)
              .setLocalUserId(config.getUid())
              .build(),
          containerOutput -> {
            try {
              return new ObjectMapper().readValue(containerOutput, HarnessResponse.class);
            } catch (IOException e) {
              return new HarnessResponse(
                  String.format(
                      "Failed to deserialise response from harness: %s. Response was %s",
                      e.getMessage(), containerOutput));
            }
          });
    } catch (ContainerExecutionException e) {
      return new ContainerExecResponse<>(
          false, new HarnessResponse(e.getMessage()), e.getMessage(), -1);
    }
  }

  /** Run the validator script by executing validator/run-validator.sh. */
  public ContainerExecResponse<ValidatorResponse> execValidator(
      File validatorDirectory,
      HarnessResponse harnessResponse,
      String imageName,
      ContainerRestrictions restrictions)
      throws ApiUnavailableException {

    Optional<String> measurements = getMeasurements(harnessResponse);
    if (!measurements.isPresent()) {
      return new ContainerExecResponse<>(
          false,
          new ValidatorResponse("Failed to serialise measurement list"),
          "Failed to serialise measurement list",
          -1);
    }

    File containerTempDir =
        new File(config.getTempRoot(), String.valueOf(tempDirCounter.incrementAndGet()));

    try (FileUtil.AutoDelete ignored = FileUtil.mkdirWithAutoDelete(containerTempDir)) {
      try (FileWriter w = new FileWriter(new File(containerTempDir, "input.json"))) {
        w.write(measurements.get());
      }
      return executeContainer(
          ExecutionConfig.builder()
              .addPathSpecification(PathSpecification.create(containerTempDir, "/input", false))
              .addPathSpecification(
                  PathSpecification.create(validatorDirectory, "/validator", false))
              .addPathSpecification(
                  PathSpecification.create(config.getLibRoot(), "/testlib", false))
              .addCommand("/validator/run-validator.sh")
              .addCommand("/validator")
              .addCommand("/testlib")
              .addCommand("/input/input.json")
              .setImageName(imageName)
              .setContainerRestrictions(restrictions)
              .setLocalUserId(config.getUid())
              .build(),
          containerResponse -> {
            try {
              return new ObjectMapper().readValue(containerResponse, ValidatorResponse.class);
            } catch (IOException e) {
              return new ValidatorResponse(
                  String.format(
                      "Failed to deserialise response from validator: %s. Response was %s.",
                      e.getMessage(), containerResponse));
            }
          });
    } catch (ContainerExecutionException | IOException e) {
      return new ContainerExecResponse<>(
          false, new ValidatorResponse(e.getMessage()), e.getMessage(), -1);
    }
  }

  private ScheduledFuture<Boolean> scheduleTimeoutKiller(
      int timeoutSec, String containerId, AttachListener attachListener) {
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

  private <T> ContainerExecResponse<T> executeContainer(
      ExecutionConfig executionConfig, Function<String, T> converter)
      throws ContainerExecutionException, ApiUnavailableException {

    String containerName =
        this.config.getContainerPrefix() + containerNameCounter.incrementAndGet();
    LOG.debug("Creating container {}", containerName);

    DockerApi docker = getDockerApi();

    long startTime = System.currentTimeMillis();
    try {
      ContainerConfig config = executionConfig.toContainerConfig();
      ContainerResponse response = docker.createContainer(containerName, config);
      final String containerId = response.getId();
      runningContainers.add(containerId);
      try {
        docker.startContainer(containerId);
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
          boolean success = false;
          boolean knownStopped = false;
          boolean closed = false;

          while (!closed) {
            closed = attachListener.waitForClose(60 * 1000);
            ContainerInfo i = docker.inspectContainer(containerId, false);
            if (!i.getState().getRunning()) {
              knownStopped = true;
              closed = true;
              success = i.getState().getExitCode() == 0;
            }
          }

          // Check to make sure its really stopped the container
          if (!knownStopped) {
            ContainerInfo containerInfo = docker.inspectContainer(containerId, false);
            if (containerInfo.getState().getRunning()) {
              DockerUtil.killContainer(containerId, docker);
              containerInfo = docker.inspectContainer(containerId, false);
            }
            success = containerInfo.getState().getExitCode() == 0;
          }

          timeoutKiller.cancel(false);
          try {
            if (timeoutKiller.get()) {
              throw new ContainerExecutionException(
                  String.format(
                      "Timed out after %d seconds", System.currentTimeMillis() - startTime));
            }
          } catch (InterruptedException | ExecutionException | CancellationException e) {
            // ignore
          }

          if (diskUsageKiller.isKilled()) {
            throw new ContainerExecutionException(
                String.format(
                    "Excessive disk usage. Recorded %d bytes written. Limit is %d mb.",
                    diskUsageKiller.getBytesWritten(),
                    executionConfig.containerRestrictions().getDiskWriteLimitMegabytes()));
          }

          try {
            session.get().close();
          } catch (InterruptedException e) {
            // ignore
          } catch (ExecutionException e) {
            LOG.error(
                "An exception occurred collecting the websocket session from the future",
                e.getCause());
          }

          LOG.debug("Container response: {}", attachListener.getOutput());
          return new ContainerExecResponse<>(
              success,
              converter.apply(attachListener.getOutput()),
              attachListener.getOutput(),
              System.currentTimeMillis() - startTime);
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

  synchronized DockerApi getDockerApi() throws ApiUnavailableException {
    if (dockerApi == null) {
      dockerApi = initializeDockerApi();
    }
    return dockerApi;
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

  private static Optional<String> getMeasurements(HarnessResponse harnessResponse) {
    ImmutableList<Measurement> m =
        harnessResponse
            .getTestParts()
            .stream()
            .map(HarnessPart::getMeasurements)
            .flatMap(List::stream)
            .collect(toImmutableList());
    try {
      return Optional.of(new ObjectMapper().writeValueAsString(m));
    } catch (JsonProcessingException e) {
      return Optional.empty();
    }
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
}
