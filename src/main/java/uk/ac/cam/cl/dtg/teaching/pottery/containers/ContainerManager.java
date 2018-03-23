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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
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

  private final ContainerEnvConfig config;
  private final ContainerBackend containerBackend;
  private final AtomicInteger tempDirCounter = new AtomicInteger(0);

  /**
   * Construct a new container manager and worker pool. The connection to the container backend is
   * created lazily as needed.
   */
  @Inject
  public ContainerManager(ContainerEnvConfig config, ContainerBackend containerBackend) {
    this.config = config;
    this.containerBackend = containerBackend;
  }

  @Override
  public void stop() {
    containerBackend.stop();
  }

  public String getApiStatus() {
    return containerBackend.getApiStatus().name();
  }

  public long getSmoothedCallTime() {
    return containerBackend.getSmoothedCallTime();
  }

  public String getVersion() throws ApiUnavailableException {
    return containerBackend.getVersion();
  }

  /**
   * Scale factor to apply to container timeouts given in container restrictions. If you are running
   * with more worker threads than cores then you might find that containers are timing out because
   * of contention. Use this method to increase all timeouts.
   */
  public void setTimeoutMultiplier(int multiplier) {
    containerBackend.setTimeoutMultiplier(multiplier);
  }

  /**
   * Compile the task and get the response. This is done by executing the compile-test.sh script in
   * the task repo.
   */
  public ContainerExecResponse<String> execTaskCompilation(
      File taskDirHost, String imageName, ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    try {
      return containerBackend.executeContainer(
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
      return ContainerExecResponse.create(false, e.getMessage(), e.getMessage(), -1);
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
      return containerBackend.executeContainer(
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
      return ContainerExecResponse.create(false, e.getMessage(), e.getMessage(), -1);
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
      return containerBackend.executeContainer(
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
      return ContainerExecResponse.create(
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
      return ContainerExecResponse.create(
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
      return containerBackend.executeContainer(
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
      return ContainerExecResponse.create(
          false, new ValidatorResponse(e.getMessage()), e.getMessage(), -1);
    }
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
}
