/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 Andrew Rice (acr31@cam.ac.uk), BlueOptima Limited
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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse.Status;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Execution;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Step;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;

@Singleton
public class ContainerManager implements Stoppable {

  private static final String POTTERY_BINARIES_PATH = "/pottery-binaries";

  private static final String IMAGE_BINDING = "IMAGE";
  private static final String SUBMISSION_BINDING = "SUBMISSION";
  private static final String VARIANT_BINDING = "VARIANT";
  private static final String TASK_BINDING = "TASK";
  private static final String STEP_BINDING = "STEP";
  private static final String SHARED_BINDING = "SHARED";

  private static final String DEFAULT_EXECUTION = "default";

  private static final Pattern COMMAND_TOKENIZER =
      Pattern.compile("([^\"' ]|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')+");

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

  private static Pattern bindingRegex = Pattern.compile("@([a-zA-Z_][-a-zA-Z_0-9]*)@");

  private ExecutionConfig.Builder applyBindings(
      String command,
      ImmutableMap<String, Binding> bindings,
      Map<String, ContainerExecResponse> stepResults,
      File containerTempDir)
      throws ContainerExecutionException {
    ExecutionConfig.Builder builder = ExecutionConfig.builder();
    StringBuilder finalCommand = new StringBuilder();

    Map<String, Binding> mutableBindings = new HashMap<String, Binding>(bindings);

    Matcher regexMatcher = bindingRegex.matcher(command);
    while (regexMatcher.find()) {
      String name = regexMatcher.group(1);
      Binding binding;
      if (mutableBindings.containsKey(name)) {
        binding = mutableBindings.get(name);
      } else if (stepResults.containsKey(name)) {
        File stepFile = new File(containerTempDir, name);
        try (FileWriter w = new FileWriter(stepFile)) {
          w.write(stepResults.get(name).response());
        } catch (IOException e) {
          throw new ContainerExecutionException(
              "Couldn't create temporary file for binding " + name + " for command " + command, e);
        }
        binding = new FileBinding(stepFile, false);
        mutableBindings.put(name, binding);
      } else {
        throw new ContainerExecutionException(
            "Couldn't find a binding called " + name + " for command " + command);
      }
      binding.applyBinding(builder, name);
      regexMatcher.appendReplacement(finalCommand, binding.getMountPoint(name));
    }
    regexMatcher.appendTail(finalCommand);

    Matcher matcher = COMMAND_TOKENIZER.matcher(finalCommand);
    while (matcher.find()) {
      builder.addCommand(matcher.group());
    }
    return builder;
  }

  abstract class Binding {
    abstract String getMountPoint(String name);

    ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name) {
      return builder;
    }
  }

  class FileBinding extends Binding {
    private final File file;
    private final boolean readWrite;
    private boolean needsApplying;

    FileBinding(File file, boolean readWrite) {
      this.file = file;
      this.readWrite = readWrite;
      this.needsApplying = true;
    }

    @Override
    ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name) {
      if (needsApplying) {
        needsApplying = false;

        return builder.addPathSpecification(
            PathSpecification.create(file, getMountPoint(name), readWrite));
      } else {
        return builder;
      }
    }

    @Override
    String getMountPoint(String name) {
      return containerBackend.getInternalMountPath() + "/" + name;
    }
  }

  class ImageBinding extends Binding {
    private final String path;

    ImageBinding(String path) {
      this.path = path;
    }

    @Override
    String getMountPoint(String name) {
      return path;
    }
  }

  class TextBinding extends Binding {
    private final String text;

    TextBinding(String text) {
      this.text = text;
    }

    /** Technically, this isn't a mount point, it's just a parameter. */
    @Override
    String getMountPoint(String name) {
      return text;
    }
  }

  /** Execute a command inside a container. */
  private ContainerExecResponse execute(
      @Nonnull Execution execution,
      Map<String, ContainerExecResponse> stepResults,
      ImmutableMap<String, Binding> bindings)
      throws ApiUnavailableException {
    File containerTempDir =
        new File(config.getTempRoot(), String.valueOf(tempDirCounter.incrementAndGet()));
    try (FileUtil.AutoDelete ignored = FileUtil.mkdirWithAutoDelete(containerTempDir)) {
      return containerBackend.executeContainer(
          applyBindings(execution.getProgram(), bindings, stepResults, containerTempDir)
              .setImageName(execution.getImage())
              .setContainerRestrictions(execution.getRestrictions())
              .setLocalUserId(config.getUid())
              .build());
    } catch (ContainerExecutionException | IOException e) {
      return ContainerExecResponse.create(Status.FAILED_UNKNOWN, e.getMessage(), -1);
    }
  }

  /** Run a compile task and get the response. */
  public ContainerExecResponse execTaskCompilation(File taskDirHost, @Nonnull Execution execution)
      throws ApiUnavailableException {
    ImmutableMap<String, Binding> bindings =
        ImmutableMap.of(
            TASK_BINDING, new FileBinding(taskDirHost, true),
            IMAGE_BINDING, new ImageBinding(POTTERY_BINARIES_PATH));
    ImmutableMap<String, ContainerExecResponse> stepResults = ImmutableMap.of();
    return execute(execution, stepResults, bindings);
  }

  /** Run a step and get the response. */
  public ContainerExecResponse execStep(
      File taskStepsDirHost,
      File codeDirHost,
      @Nonnull Execution execution,
      String variant,
      Map<String, ContainerExecResponse> stepResults)
      throws ApiUnavailableException {
    ImmutableMap<String, Binding> bindings =
        ImmutableMap.of(
            IMAGE_BINDING, new ImageBinding(POTTERY_BINARIES_PATH),
            SUBMISSION_BINDING, new FileBinding(codeDirHost, true),
            STEP_BINDING, new FileBinding(new File(taskStepsDirHost, variant), false),
            SHARED_BINDING, new FileBinding(new File(taskStepsDirHost, "shared"), false),
            VARIANT_BINDING, new TextBinding(variant));
    return execute(execution, stepResults, bindings);
  }

  public interface StepRunnerCallback {
    void setStatus(String status);

    void recordErrorReason(ContainerExecResponse response, String stepName);

    void startStep(String stepName);

    void finishStep(String stepName, String status, long msec, String output);
  }

  public interface ErrorHandlingStepRunnerCallback extends StepRunnerCallback {
    void apiUnavailable(String errorMessage, Throwable exception);
  }

  public int runSteps(
      TaskCopy c,
      File codeDir,
      TaskInfo taskInfo,
      String action,
      String variant,
      ErrorHandlingStepRunnerCallback callback) {
    try {
      // The StepRunnerCallback cast is necessary to prevent a stack overflow since this would
      // become self-recursive
      return runSteps(c, codeDir, taskInfo, action, variant, (StepRunnerCallback) callback);
    } catch (ApiUnavailableException e) {
      callback.apiUnavailable(e.getMessage(), e.getCause());
      return Job.STATUS_RETRY;
    }
  }

  public int runSteps(
      TaskCopy c,
      File codeDir,
      TaskInfo taskInfo,
      String action,
      String variant,
      StepRunnerCallback callback)
      throws ApiUnavailableException {
    callback.setStatus(Submission.STATUS_RUNNING);

    Map<String, ContainerExecResponse> stepResults = new HashMap<>();

    List<String> steps = taskInfo.getActions().get(action).getSteps();

    for (String stepName : steps) {

      Step step = taskInfo.getSteps().get(stepName);
      Map<String, Execution> executionMap = step.getExecutionMap();
      Execution execution = getExecution(variant, executionMap);
      if (execution == null) {
        continue;
      }
      callback.startStep(stepName);
      try {
        ContainerExecResponse response =
            execStep(c.getStepLocation(stepName), codeDir, execution, variant, stepResults);
        stepResults.put(stepName, response);
        callback.finishStep(
            stepName,
            response.status() == Status.COMPLETED
                ? Submission.STATUS_COMPLETE
                : Submission.STATUS_FAILED,
            response.executionTimeMs(),
            response.response());
        if (response.status() != Status.COMPLETED) {
          callback.setStatus(Submission.STATUS_FAILED);
          callback.recordErrorReason(response, stepName);
          if (response.status() != Status.FAILED_EXITCODE) {
            return Job.STATUS_FAILED;
          }
          break;
        }
      } catch (ApiUnavailableException e) {
        throw new ApiUnavailableException(
            "Container API unavailable when trying to execute " + stepName + " step.", e);
      }
    }

    return Job.STATUS_OK;
  }

  @Nullable
  private Execution getExecution(String variant, Map<String, Execution> executionMap) {
    if (executionMap.containsKey(variant)) {
      return executionMap.get(variant);
    } else if (executionMap.containsKey(DEFAULT_EXECUTION)) {
      return executionMap.get(DEFAULT_EXECUTION);
    } else {
      return null;
    }
  }
}
