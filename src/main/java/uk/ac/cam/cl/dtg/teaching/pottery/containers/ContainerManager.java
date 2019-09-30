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

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse.Status;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.taint.Taint;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Action;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Execution;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Step;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskDetail;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;

@Singleton
public class ContainerManager implements Stoppable {
  protected static final Logger LOG = LoggerFactory.getLogger(ContainerManager.class);

  static final String DEFAULT_EXECUTION = "default";

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

  private Cache<String, String> executionCache = CacheBuilder.newBuilder().softValues().build();

  /** Execute a command inside a container. */
  private ContainerExecResponse execute(
      @Nonnull Execution execution, ExecutionConfig.Builder bindingsBuilder, String partialCacheKey)
      throws ApiUnavailableException, ContainerExecutionException {
    ExecutionConfig executionConfig =
        bindingsBuilder
            .setImageName(execution.getImage())
            .setContainerRestrictions(execution.getRestrictions())
            .setLocalUserId(config.getUid())
            .build();

    if (partialCacheKey != null && !executionConfig.taint().isUserControlled()) {
      String fullCacheKey =
          executionConfig.imageName()
              + ":"
              + executionConfig.configurationHash()
              + ":"
              + executionConfig
                  .taint()
                  .name() // Not identity; even if not user controlled, still user specific.
              + ":"
              + partialCacheKey;

      String result = executionCache.getIfPresent(fullCacheKey);

      if (result != null) {
        LOG.info(
            "Returning from cache (" + executionConfig.taint() + "):" + executionConfig.command());
        return ContainerExecResponse.create(Status.COMPLETED, result, 0, executionConfig.taint());
      }

      LOG.info(
          "Running and caching (" + executionConfig.taint() + "):" + executionConfig.command());
      ContainerExecResponse response = containerBackend.executeContainer(executionConfig);
      if (response.status() == Status.COMPLETED) {
        // Only cache success
        executionCache.put(fullCacheKey, response.response());
      }
      return response;
    } else {
      LOG.info("Executing (" + executionConfig.taint() + "):" + executionConfig.command());
      return containerBackend.executeContainer(executionConfig);
    }
  }

  /** Run a compile task and get the response. */
  public ContainerExecResponse execTaskCompilation(File taskDirHost, @Nonnull Execution execution)
      throws ApiUnavailableException {
    ImmutableMap<String, Binding> bindings =
        baseImageBinding()
            .put(
                Binding.TASK_BINDING,
                new Binding.FileBinding(
                    taskDirHost,
                    true,
                    containerBackend.getInternalMountPath(),
                    Binding.Control.FROM_TASK))
            .build();
    try {
      return execute(
          execution,
          Binding.applyBindings(execution.getProgram(), bindings, stepName -> null, Taint.Compile),
          null);
    } catch (ContainerExecutionException e) {
      return ContainerExecResponse.create(Status.FAILED_UNKNOWN, e.getMessage(), -1, Taint.Compile);
    }
  }

  /** Run a step and get the response. */
  private ContainerExecResponse execStep(
      String stepName,
      File taskStepsDirHost,
      File codeDirHost,
      File taskCommonDirHost,
      @Nonnull Execution execution,
      RepoInfo repoInfo,
      Map<String, ContainerExecResponse> stepResults,
      Taint taint)
      throws ApiUnavailableException {
    ImmutableMap<String, Binding> bindings =
        addRepoInfoToBinding(baseImageBinding(), repoInfo)
            .put(
                Binding.SUBMISSION_BINDING,
                new Binding.FileBinding(
                    codeDirHost,
                    true,
                    containerBackend.getInternalMountPath(),
                    Binding.Control.USER_CONTROLLED))
            .put(
                Binding.STEP_BINDING,
                new Binding.FileBinding(
                    new File(taskStepsDirHost, repoInfo.getVariant()),
                    false,
                    containerBackend.getInternalMountPath(),
                    Binding.Control.FROM_TASK))
            .put(
                Binding.SHARED_BINDING,
                new Binding.FileBinding(
                    new File(taskStepsDirHost, "shared"),
                    false,
                    containerBackend.getInternalMountPath(),
                    Binding.Control.FROM_TASK))
            .put(
                Binding.COMMON_BINDING,
                new Binding.FileBinding(
                    taskCommonDirHost,
                    false,
                    containerBackend.getInternalMountPath(),
                    Binding.Control.FROM_TASK))
            .build();

    File containerTempDir =
        new File(config.getTempRoot(), String.valueOf(tempDirCounter.incrementAndGet()));
    try (FileUtil.AutoDelete ignored = FileUtil.mkdirWithAutoDelete(containerTempDir)) {
      ExecutionConfig.Builder executionConfig =
          Binding.applyBindings(
              execution.getProgram(),
              bindings,
              step -> {
                if (stepResults.containsKey(step)) {
                  ContainerExecResponse stepResult = stepResults.get(step);
                  Taint stepTaint = stepResult.taint();
                  return new Binding.TemporaryFileBinding(
                      containerTempDir,
                      stepResult.response(),
                      containerBackend.getInternalMountPath(),
                      stepTaint.isUserControlled());
                }
                return null;
              },
              taint);
      return execute(execution, executionConfig, stepName);
    } catch (ContainerExecutionException | IOException e) {
      return ContainerExecResponse.create(Status.FAILED_UNKNOWN, e.getMessage(), -1, taint);
    }
  }

  private ImmutableMap.Builder<String, Binding> baseImageBinding() {
    return ImmutableMap.<String, Binding>builder()
        .put(Binding.IMAGE_BINDING, new Binding.ImageBinding(Binding.POTTERY_BINARIES_PATH));
  }

  private ImmutableMap.Builder<String, Binding> addRepoInfoToBinding(
      ImmutableMap.Builder<String, Binding> binding, RepoInfo repoInfo) {
    return binding
        .put(Binding.VARIANT_BINDING, new Binding.TextBinding(repoInfo.getVariant()))
        .put(Binding.MUTATION_ID_BINDING, new Binding.TextBinding("" + repoInfo.getMutationId()));
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
      TaskDetail taskDetail,
      String action,
      RepoInfo repoInfo,
      ErrorHandlingStepRunnerCallback callback) {
    try {
      // The StepRunnerCallback cast is necessary to prevent a stack overflow since this would
      // become self-recursive
      return runSteps(c, codeDir, taskDetail, action, repoInfo, (StepRunnerCallback) callback);
    } catch (ApiUnavailableException e) {
      callback.apiUnavailable(e.getMessage(), e.getCause());
      return Job.STATUS_RETRY;
    }
  }

  public int runSteps(
      TaskCopy c,
      File codeDir,
      TaskDetail taskDetail,
      String action,
      RepoInfo repoInfo,
      StepRunnerCallback callback)
      throws ApiUnavailableException {
    callback.setStatus(Submission.STATUS_RUNNING);

    Map<String, ContainerExecResponse> stepResults = new HashMap<>();
    File commonDirHost = new File(c.getLocation(), "common");

    if (action == null && taskDetail.getActions().size() == 1) {
      action = Iterables.getOnlyElement(taskDetail.getActions().keySet());
    }

    Action actionDetails = taskDetail.getActions().get(action);
    if (actionDetails == null) {
      callback.setStatus(Submission.STATUS_FAILED);
      callback.recordErrorReason(
          ContainerExecResponse.create(
              Status.FAILED_EXITCODE, "Failed to find action named " + action, 0, null),
          "SETUP");
      return Job.STATUS_FAILED;
    }

    List<String> steps = actionDetails.getSteps();

    for (String stepName : steps) {
      Step step = taskDetail.getSteps().get(stepName);
      Execution execution = getExecution(repoInfo, step);
      if (execution == null) {
        continue;
      }
      callback.startStep(stepName);
      try {
        ContainerExecResponse response =
            execStep(
                stepName,
                c.getStepLocation(stepName),
                codeDir,
                commonDirHost,
                execution,
                repoInfo,
                stepResults,
                new Taint(repoInfo.getRepoId(), false));
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
          return Job.STATUS_FAILED;
        }
      } catch (ApiUnavailableException e) {
        throw new ApiUnavailableException(
            "Container API unavailable when trying to execute " + stepName + " step.", e);
      }
    }

    return Job.STATUS_OK;
  }

  public ContainerExecResponse runParameterisation(TaskCopy c, File codeDir, RepoInfo repoInfo)
      throws ApiUnavailableException {
    Preconditions.checkNotNull(c.getDetail().getParameterisation());

    Step step = c.getDetail().getParameterisation().getGenerator();
    Execution execution = getExecution(repoInfo, step);
    Preconditions.checkNotNull(execution);

    ImmutableMap<String, Binding> bindings =
        addRepoInfoToBinding(baseImageBinding(), repoInfo)
            .put(
                Binding.TASK_BINDING,
                new Binding.FileBinding(
                    c.getLocation(),
                    false,
                    containerBackend.getInternalMountPath(),
                    Binding.Control.FROM_TASK))
            .put(
                Binding.SUBMISSION_BINDING,
                new Binding.FileBinding(
                    codeDir,
                    true,
                    containerBackend.getInternalMountPath(),
                    Binding.Control.USER_CONTROLLED))
            .build();
    Taint taint = Taint.parameterisation(repoInfo.getRepoId());
    try {
      return execute(
          execution,
          Binding.applyBindings(execution.getProgram(), bindings, stepName -> null, taint),
          null);
    } catch (ContainerExecutionException e) {
      return ContainerExecResponse.create(Status.FAILED_UNKNOWN, e.getMessage(), -1, taint);
    }
  }

  @Nullable
  private Execution getExecution(RepoInfo repoInfo, Step step) {
    Map<String, Execution> executionMap = step.getExecutionMap();
    String variant = repoInfo.getVariant();
    if (executionMap.containsKey(variant)) {
      return executionMap.get(variant);
    } else if (executionMap.containsKey(DEFAULT_EXECUTION)) {
      return executionMap.get(DEFAULT_EXECUTION);
    } else {
      return null;
    }
  }
}
