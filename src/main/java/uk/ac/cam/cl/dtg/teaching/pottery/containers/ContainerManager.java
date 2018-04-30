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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse.Status;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.*;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;

@Singleton
public class ContainerManager implements Stoppable {

  public static final String POTTERY_BINARIES_PATH = "/pottery-binaries";
  public static final String IMAGE_BINDING = "IMAGE";
  public static final String SUBMISSION_BINDING = "SUBMISSION";
  public static final String VARIANT_BINDING = "VARIANT";
  public static final String TASK_BINDING = "TASK";

  public static final String DEFAULT_EXECUTION = "default";

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

  private static Pattern bindingRegex = Pattern.compile("@([a-zA-Z_][a-zA-Z_0-9]*)@");
  private ExecutionConfig.Builder applyBindings(String command, Map<String, Binding> bindings,
                                                Map<String, ContainerExecResponse> stepResults, File containerTempDir)
      throws ContainerExecutionException, IOException {
    ExecutionConfig.Builder builder = ExecutionConfig.builder();
    StringBuffer finalCommand = new StringBuffer();

    Matcher regexMatcher = bindingRegex.matcher(command);
    // FIXME: Matcher has a replaceAll that takes a function
    while (regexMatcher.find()) {
      String name = regexMatcher.group(1);
      Binding binding;
      if (bindings.containsKey(name)) {
        binding = bindings.get(name);
      } else if (stepResults.containsKey(name)) {
        File stepFile = new File(containerTempDir, name);
        try (FileWriter w = new FileWriter(stepFile)) {
          w.write(stepResults.get(name).response());
        }
        binding = new FileBinding(stepFile, false);
      } else {
        throw new ContainerExecutionException("Couldn't find a binding called " + name + " for command " + command);
      }
      binding.applyBinding(builder, name);
      regexMatcher.appendReplacement(finalCommand, binding.getMountPoint(name));
    }
    regexMatcher.appendTail(finalCommand);

    Pattern tokenizer = Pattern.compile("([^\"' ]|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')+");
    Matcher matcher = tokenizer.matcher(finalCommand);
    while(matcher.find()) {
      builder.addCommand(matcher.group());
    }
    return builder;
  }

  static abstract class Binding {
    abstract public ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name);
    abstract public String getMountPoint(String name);
  }

  static class FileBinding extends Binding {
    private final File file;
    private final boolean readWrite;

    public FileBinding(File file, boolean readWrite) {
      this.file = file;
      this.readWrite = readWrite;
    }

    @Override
    public ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name) {
      return builder.addPathSpecification(PathSpecification.create(file, getMountPoint(name), readWrite));
    }

    @Override
    public String getMountPoint(String name) {
      return "/mnt/pottery/" + name;
    }
  }

  static class ImageBinding extends Binding {
    private final String path;

    public ImageBinding(String path) {
      this.path = path;
    }

    @Override
    public String getMountPoint(String name) {
      return path;
    }

    @Override
    public ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name) {
      return builder;
    }
  }

  static class TextBinding extends Binding {
    private final String text;

    public TextBinding(String text) {
      this.text = text;
    }

    @Override
    public ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name) {
      return builder;
    }

    @Override
    public String getMountPoint(String name) {
      return text;
    }
  }

  /**
   * Execute a command inside a container
   */
  private ContainerExecResponse execute(String imageName,
                                                String command,
                                                ContainerRestrictions restrictions,
                                                Map<String, Binding> bindings,
                                                Map<String, ContainerExecResponse> stepResults) throws ApiUnavailableException {
    File containerTempDir = new File(config.getTempRoot(), String.valueOf(tempDirCounter.incrementAndGet()));
    try (FileUtil.AutoDelete ignored = FileUtil.mkdirWithAutoDelete(containerTempDir)) {
      return containerBackend.executeContainer(
          applyBindings(command, bindings, stepResults, containerTempDir)
              .setImageName(imageName)
              .setContainerRestrictions(restrictions)
              .setLocalUserId(config.getUid())
              .build());
    } catch (ContainerExecutionException | IOException e) {
      return ContainerExecResponse.create(
          Status.FAILED_UNKNOWN, -1, e.getMessage(), -1);
    }
  }

  /**
   * Run a compile task and get the response.
   */
  public ContainerExecResponse execTaskCompilation(
      File taskDirHost, String imageName, String command, ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    Map<String, Binding> bindings = Map.of(
        TASK_BINDING, new FileBinding(taskDirHost, true),
        IMAGE_BINDING, new ImageBinding(POTTERY_BINARIES_PATH));
    Map<String, ContainerExecResponse> stepResults = Collections.emptyMap();
    return execute(imageName, command, restrictions, bindings, stepResults);
  }

  /**
   * Run a step and get the response.
   */
  public ContainerExecResponse execStep(
      File taskStepsDirHost, File codeDirHost, String imageName, String command,String variant,
      Map<String, ContainerExecResponse> stepResults, ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    Map<String, Binding> bindings = Map.of(
        IMAGE_BINDING, new ImageBinding(POTTERY_BINARIES_PATH),
        SUBMISSION_BINDING, new FileBinding(codeDirHost, true),
        "STEP", new FileBinding(new File(taskStepsDirHost, variant), false),
        "SHARED", new FileBinding(new File(taskStepsDirHost, "shared"), false),
        VARIANT_BINDING, new TextBinding(variant)
        );
    return execute(imageName, command, restrictions, bindings, stepResults);
  }

  /**
   * Run a output and get the response.
   */
  public ContainerExecResponse execOutput(
      File taskDirHost, File codeDirHost, String imageName, String command, String variant,
      Map<String, ContainerExecResponse> stepResults,
      Map<String, String> potteryProperties,
      ContainerRestrictions restrictions)
      throws ApiUnavailableException {
    Map<String, Binding> bindings = Map.of(
        IMAGE_BINDING, new ImageBinding(POTTERY_BINARIES_PATH),
        TASK_BINDING, new FileBinding(taskDirHost, true),
        SUBMISSION_BINDING, new FileBinding(codeDirHost, true),
        VARIANT_BINDING, new TextBinding(variant)
    );
    command = command + " " + stepResults.entrySet().stream().map(entry -> String.format("--input=%1$s:%2$s:%3$s:@%1$s@", entry.getKey(), entry.getValue().exitCode(), entry.getValue().executionTimeMs())).collect(Collectors.joining(" "));
    command = command + " " + potteryProperties.entrySet().stream().map(entry -> String.format("--pottery-%1$s=%2$s", entry.getKey(), entry.getValue())).collect(Collectors.joining(" "));
    return execute(imageName, command, restrictions, bindings, stepResults);
  }

  public interface StepRunnerCallback {
    void setStatus(String status);
    void recordErrorReason(ContainerExecResponse response, String stepName);
    void setOutput(String output);
  }

  public interface ErrorHandlingStepRunnerCallback extends StepRunnerCallback {
    void apiUnavailable(String errorMessage, Throwable exception);
  }

  public int runStepsAndOutput(TaskCopy c, File codeDir, TaskInfo taskInfo, String variant, ErrorHandlingStepRunnerCallback callback) {
    try {
      return runStepsAndOutput(c, codeDir, taskInfo, variant, (StepRunnerCallback) callback);
    } catch (ApiUnavailableException e) {
      callback.apiUnavailable(e.getMessage(), e.getCause());
      return Job.STATUS_RETRY;
    }
  }

  public int runStepsAndOutput(TaskCopy c, File codeDir, TaskInfo taskInfo, String variant, StepRunnerCallback callback) throws ApiUnavailableException {
    callback.setStatus(Submission.STATUS_STEPS_RUNNING);

    Map<String, ContainerExecResponse> stepResults = new HashMap<>();
    ContainerExecResponse response = null;

    String stepName = null;

    for (Step step : taskInfo.getSteps()) {
      stepName = step.getName();
      Map<String, Execution> executionMap = step.getExecution();
      Execution execution = getExecution(variant, executionMap);
      if (execution == null) continue;
      try {
        response =
            execStep(
                new File(c.getStepsLocation(), stepName),
                codeDir,
                execution.getImage(),
                execution.getProgram(),
                variant,
                stepResults,
                execution.getRestrictions());
      } catch (ApiUnavailableException e) {
        throw new ApiUnavailableException("Docker API unavailable when trying to execute " + stepName + " step.", e);
      }
      stepResults.put(stepName, response);
      if (response.status() != Status.COMPLETED) {
        break;
      }
    }

    if (response != null && response.status() != Status.COMPLETED && response.status() != Status.ERROR) {
      callback.setStatus(Submission.STATUS_STEPS_FAILED);
      callback.recordErrorReason(response, stepName);
      return Job.STATUS_FAILED;
    }

    callback.setStatus(Submission.STATUS_OUTPUT_RUNNING);

    Execution execution = getExecution(variant, taskInfo.getOutput());

    ContainerExecResponse output;
    try {
      output = execOutput(
          c.getLocation(),
          codeDir,
          execution.getImage(),
          execution.getProgram(),
          variant,
          stepResults,
          Map.of(),
          execution.getRestrictions()
      );
    } catch (ApiUnavailableException e) {
      throw new ApiUnavailableException("Docker API unavailable when trying to execute output", e);
    }

    callback.setOutput(output.response());

    if (output.status() != Status.COMPLETED && output.status() != Status.ERROR) {
      callback.setStatus(Submission.STATUS_OUTPUT_FAILED);
      callback.recordErrorReason(response, null);
      return Job.STATUS_FAILED;
    }
    return Job.STATUS_OK;
  }

  private Execution getExecution(String variant, Map<String, Execution> executionMap) {
    Execution execution;
    if (executionMap.containsKey(variant)) {
      execution = executionMap.get(variant);
    } else if(executionMap.containsKey(DEFAULT_EXECUTION)) {
      execution = executionMap.get(DEFAULT_EXECUTION);
    } else {
      execution = null;
    }
    return execution;
  }
}
