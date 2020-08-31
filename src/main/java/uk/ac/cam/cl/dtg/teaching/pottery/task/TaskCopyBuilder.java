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
package uk.ac.cam.cl.dtg.teaching.pottery.task;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse.Status;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCopyNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.BuilderInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

/**
 * Class for building a taskcopy. Responsible for copying files, compilation etc.
 *
 * @author acr31
 */
public class TaskCopyBuilder {

  protected static final Logger LOG = LoggerFactory.getLogger(TaskCopyBuilder.class);

  /** DTO with information about the current state of this copy. */
  private final BuilderInfo builderInfo;
  /** Worker object for copying files into this TaskCopy. */
  private final Job copyFiles;
  /** Worker object for compiling the tests in this TaskCopy. */
  private final Job compileTests;
  /**
   * The TaskCopy object that this builder is building. Starts off as null and then gets set
   * asynchronously.
   */
  private volatile TaskCopy taskCopy;

  /**
   * Instances of this class should be created by the Task object only. The task object has to
   * ensure that there is only one instance per copyId.
   */
  private TaskCopyBuilder(
      String sha1, String taskId, URI taskDefLocation, String copyId, TaskConfig taskConfig) {
    // We know that noone else will have access to the TaskCopy until we are done
    // 1) our copyId is unique and Task ensures that there is only one instance of TaskCopyBuilder
    // for each copyId
    // 2) we only give out a reference to the TaskCopy once the BuilderInfo status is set to
    // success. And this only happens after we finish
    // Therefore no locks are needed on this lot

    this.builderInfo = new BuilderInfo(sha1);
    this.taskCopy = null;
    this.copyFiles =
        new Job() {
          @Override
          public int execute(
              TaskIndex taskIndex,
              RepoFactory repoFactory,
              ContainerManager containerManager,
              Database database) {
            return copyFiles(sha1, taskId, copyId, taskConfig, taskDefLocation)
                ? Job.STATUS_OK
                : Job.STATUS_FAILED;
          }

          @Override
          public String getDescription() {
            return "Copy files into copy of task " + taskId;
          }
        };
    this.compileTests =
        new Job() {
          @Override
          public int execute(
              TaskIndex taskIndex,
              RepoFactory repoFactory,
              ContainerManager containerManager,
              Database database) {
            try {
              return compileFiles(containerManager) ? Job.STATUS_OK : Job.STATUS_FAILED;
            } catch (ApiUnavailableException e) {
              LOG.warn("Docker API unavailable. Retrying", e);
              return Job.STATUS_RETRY;
            }
          }

          @Override
          public String getDescription() {
            return "Compile tests for task " + taskId;
          }
        };
  }

  static TaskCopyBuilder createNew(
      String sha1, String taskId, URI taskDefLocation, String copyId, TaskConfig taskConfig) {
    return new TaskCopyBuilder(sha1, taskId, taskDefLocation, copyId, taskConfig);
  }

  static TaskCopyBuilder createForExisting(
      String sha1, String taskId, URI taskDefLocation, String copyId, TaskConfig taskConfig)
      throws InvalidTaskSpecificationException, TaskCopyNotFoundException, TaskStorageException {
    TaskCopyBuilder result = new TaskCopyBuilder(sha1, taskId, taskDefLocation, copyId, taskConfig);
    if (taskConfig.getTaskCopyDir(copyId).exists()) {
      result.taskCopy = new TaskCopy(taskId, copyId, taskConfig);
      result.builderInfo.setStatus(BuilderInfo.STATUS_SUCCESS);
    } else {
      throw new TaskCopyNotFoundException(
          "Task copy " + copyId + " for task " + taskId + " not found");
    }
    return result;
  }

  /** Create a placeholder TaskCopyBuilder to represent that no TaskCopy has been built. */
  static TaskCopyBuilder createSuccessPlaceholder(String taskId, TaskConfig taskConfig) {
    TaskCopyBuilder result = new TaskCopyBuilder("HEAD", taskId, null, null, taskConfig);
    result.builderInfo.setStatus(BuilderInfo.STATUS_SUCCESS);
    return result;
  }

  /**
   * Create a placeholder TaskCopyBuilder to represent that something went wrong in intialising the
   * TaskCopy process.
   */
  static TaskCopyBuilder createFailurePlaceholder(
      String taskId, TaskConfig taskConfig, Exception e) {
    TaskCopyBuilder result = new TaskCopyBuilder("INVALID", taskId, null, null, taskConfig);
    result.builderInfo.setException(e);
    return result;
  }

  TaskCopy getTaskCopy() {
    // Don't give out TaskCopy objects until we've finished building
    if (builderInfo.getStatus().equals(BuilderInfo.STATUS_SUCCESS)) {
      return taskCopy;
    } else {
      return null;
    }
  }

  /** Schedule the copying and compilation for this copy. */
  void schedule(Worker w, Job continuation) {
    // synchronize here to eliminate the race between checking the status and marking us scheduled
    synchronized (builderInfo) {
      if (isReplacable()) {
        builderInfo.setStatus(BuilderInfo.STATUS_SCHEDULED);
        w.schedule(copyFiles, compileTests, continuation);
      }
    }
  }

  BuilderInfo getBuilderInfo() {
    return builderInfo;
  }

  boolean isReplacable() {
    String status = this.builderInfo.getStatus();
    return BuilderInfo.STATUS_NOT_STARTED.equals(status)
        || BuilderInfo.STATUS_SUCCESS.equals(status)
        || BuilderInfo.STATUS_FAILURE.equals(status);
  }

  private boolean copyFiles(
      String sha1, String taskId, String copyId, TaskConfig taskConfig, final URI taskDefLocation) {
    // We are the only object writing to this directory (we have a unique id)
    // As many threads as you like can read from the bare git repo
    // Assignments to builderInfo are atomic
    // => No locks needed

    builderInfo.setStatus(BuilderInfo.STATUS_COPYING_FILES);
    LOG.info("Copying files for {} into {}", taskDefLocation, copyId);
    File location = taskConfig.getTaskCopyDir(copyId);

    // copy the files from the repo
    try (Git g =
        Git.cloneRepository().setURI(taskDefLocation.toString()).setDirectory(location).call()) {
      g.reset().setMode(ResetType.HARD).setRef(sha1).call();
    } catch (GitAPIException e) {
      builderInfo.setException(
          new TaskStorageException(
              "Failed to create clone of " + taskDefLocation + " and reset to " + sha1, e));
      return false;
    }

    try {
      taskCopy = new TaskCopy(taskId, copyId, taskConfig);
    } catch (InvalidTaskSpecificationException | TaskStorageException e) {
      builderInfo.setException(e);
      return false;
    }

    return true;
  }

  private boolean compileFiles(ContainerManager containerManager) throws ApiUnavailableException {
    String copyId = taskCopy.getCopyId();
    TaskDetail taskDetail = taskCopy.getDetail();

    LOG.info("Compiling tests for task {} in {}", taskDetail.getTaskId(), copyId);

    builderInfo.setStatus(BuilderInfo.STATUS_COMPILING_TESTS);

    for (Execution compileStep : taskDetail.getTaskCompilation()) {
      ContainerExecResponse r =
          containerManager.execTaskCompilation(taskCopy.getLocation(), compileStep);

      switch (r.status()) {
        case FAILED_UNKNOWN:
          builderInfo.setException(
              new InvalidTaskSpecificationException(
                  "Failed to compile testing code in task. "
                      + "Compiler response was: "
                      + r.response()));
          break;
        case FAILED_DISK:
          builderInfo.setException(
              new InvalidTaskSpecificationException(
                  "Insufficient disk quota to compile testing code in task. "
                      + "Compiler response was: "
                      + r.response()));
          break;
        case FAILED_OOM:
          builderInfo.setException(
              new InvalidTaskSpecificationException(
                  "Insufficient memory quota to compile testing code in task. "
                      + "Compiler response was: "
                      + r.response()));
          break;
        case FAILED_TIMEOUT:
          builderInfo.setException(
              new InvalidTaskSpecificationException(
                  "Timeout when compiling testing code in task. "
                      + "Compiler response was: "
                      + r.response()));
          break;
        case FAILED_EXITCODE:
          builderInfo.setException(
              new InvalidTaskSpecificationException(
                  "Compiler returned an error code. " + "Compiler response was: " + r.response()));
          break;
        default:
          break;
      }
      if (!r.status().equals(Status.COMPLETED)) {
        return false;
      }
      String response = r.response();

      builderInfo.addTestCompileResponse(response);
    }

    builderInfo.setStatus(BuilderInfo.STATUS_TESTING_SOLUTIONS);

    for (String variant : taskDetail.getVariants()) {
      List<Testcase> testcases =
          taskDetail.getTaskTests().getOrDefault(variant, ImmutableList.of());
      File variantSolutions = taskCopy.getSolutionLocation(variant);
      for (Testcase testcase : testcases) {
        String testName = testcase.getDirectory();
        File testCodeFolder = new File(variantSolutions, testName);
        String testExpectedFailureStep = testcase.getExpectedFailureStep();

        final String taskName = variant + "/" + testName;
        final AtomicBoolean failedAsExpected = new AtomicBoolean(false);

        int result =
            containerManager.runSteps(
                taskCopy,
                testCodeFolder,
                taskDetail,
                testcase.getAction(),
                new RepoInfo("", "", false, new Date(), variant, null, null, 0, null),
                new ContainerManager.StepRunnerCallback() {
                  @Override
                  public void setStatus(String status) {
                    LOG.info("{}: {}", taskName, status);
                  }

                  @Override
                  public void recordErrorReason(ContainerExecResponse response, String stepName) {
                    LOG.info(taskName + ": recordErrorReason " + response + " at " + stepName);
                    if (testExpectedFailureStep != null
                        && testExpectedFailureStep.equals(stepName)) {
                      // All good, expected to fail here
                      failedAsExpected.set(true);
                    } else {
                      switch (response.status()) {
                        case FAILED_UNKNOWN:
                          builderInfo.setException(
                              new InvalidTaskSpecificationException(
                                  "Failed when testing "
                                      + taskName
                                      + " at step "
                                      + stepName
                                      + " during registration. "
                                      + "Compiler response was: "
                                      + response.response()));
                          break;
                        case FAILED_DISK:
                          builderInfo.setException(
                              new InvalidTaskSpecificationException(
                                  "Insufficient disk quota when testing "
                                      + taskName
                                      + " at step "
                                      + stepName
                                      + " during registration. "
                                      + "Compiler response was: "
                                      + response.response()));
                          break;
                        case FAILED_OOM:
                          builderInfo.setException(
                              new InvalidTaskSpecificationException(
                                  "Insufficient memory when testing "
                                      + taskName
                                      + " at step "
                                      + stepName
                                      + " during registration. "
                                      + "Compiler response was: "
                                      + response.response()));
                          break;
                        case FAILED_TIMEOUT:
                          builderInfo.setException(
                              new InvalidTaskSpecificationException(
                                  "Timeout when testing "
                                      + taskName
                                      + " at step "
                                      + stepName
                                      + " during registration. "
                                      + "Compiler response was: "
                                      + response.response()));
                          break;
                        case FAILED_EXITCODE:
                          builderInfo.setException(
                              new InvalidTaskSpecificationException(
                                  "Bad exit code for "
                                      + taskName
                                      + " at step "
                                      + stepName
                                      + " during registration. "
                                      + "Compiler response was: "
                                      + response.response()));
                          break;
                        case COMPLETED:
                        default:
                          break;
                      }
                    }
                  }

                  @Override
                  public void startStep(String stepName) {
                    // Don't care about the actual operation of steps
                  }

                  @Override
                  public void finishStep(
                      String stepName,
                      String status,
                      long msec,
                      String output,
                      String containerName) {
                    // Don't care about the actual operation of steps or the output
                  }
                });
        if (testExpectedFailureStep != null) {
          if (!failedAsExpected.get()) {
            builderInfo.setStatus(BuilderInfo.STATUS_FAILURE);
            builderInfo.addSolutionTestingResponse(
                taskName
                    + " was expected to fail at step "
                    + testExpectedFailureStep
                    + " but it didn't");
            LOG.info(
                "{}: {}",
                taskName,
                "Expected step " + testExpectedFailureStep + " to fail, but it didn't");
            return false;
          }
          builderInfo.addSolutionTestingResponse(
              taskName + " failed at step " + testExpectedFailureStep + " as expected");
        } else {
          if (result != Job.STATUS_OK) {
            builderInfo.setStatus(BuilderInfo.STATUS_FAILURE);
            builderInfo.addSolutionTestingResponse(
                taskName
                    + " was expected to succeed but did not"
                    + "\r\n"
                    + "Exception was "
                    + builderInfo.getException());
            LOG.info("{}: {}", taskName, "Output script didn't return OK");
            return false;
          }
          builderInfo.addSolutionTestingResponse(taskName + " succeeded as expected");
        }
      }
    }

    LOG.info("Success in running the tests for the task");

    builderInfo.setStatus(BuilderInfo.STATUS_SUCCESS);

    return true;
  }
}
