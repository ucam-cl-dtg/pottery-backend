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
package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.eclipse.jgit.lib.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoFileNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoTagNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskMissingVariantException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.FileData;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfoWithStatus;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoTag;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class RepoController implements uk.ac.cam.cl.dtg.teaching.pottery.api.RepoController {

  protected static final Logger LOG = LoggerFactory.getLogger(RepoController.class);
  private RepoFactory repoFactory;
  private TaskIndex taskIndex;
  private Worker worker;

  /** Create a new RepoController. */
  @Inject
  public RepoController(RepoFactory repoFactory, TaskIndex taskIndex,
                        @Named(Repo.PARAMETERISATION_WORKER_NAME) Worker worker) {
    super();
    this.repoFactory = repoFactory;
    this.taskIndex = taskIndex;
    this.worker = worker;
  }

  @Override
  public RepoInfoWithStatus makeRemoteRepo(
      String taskId,
      Boolean usingTestingVersionBoolean,
      Integer validityMinutesInteger,
      String variant,
      String remote,
      Integer seed)
      throws TaskNotFoundException, RepoStorageException, RetiredTaskException,
      RepoNotFoundException, TaskMissingVariantException {
    if (taskId == null) {
      throw new TaskNotFoundException("No taskId specified");
    }
    boolean usingTestingVersion = usingTestingVersionBoolean == null ? false
        : usingTestingVersionBoolean;
    if (remote == null) {
      throw new TaskNotFoundException("No remote specified");
    }
    Task t = taskIndex.getTask(taskId);
    if (t.isRetired()) {
      throw new RetiredTaskException("Cannot start a new repository for task " + taskId);
    }
    int mutationId;
    try (TaskCopy c = usingTestingVersion ? t.acquireTestingCopy() : t.acquireRegisteredCopy()) {
      if (!c.getInfo().getVariants().contains(variant)) {
        throw new TaskMissingVariantException("Variant " + variant + " is not defined");
      }
      mutationId = Optional.ofNullable(c.getDetail().getParameterisation())
          .map(p -> seed % p.getCount()).orElse(-1);
    }
    Repo r = repoFactory.createInstance(taskId, usingTestingVersion, null, variant,
        remote, mutationId);
    String repoId = r.getRepoId();
    worker.schedule(
        new Job() {
          @Override
          public int execute(
              TaskIndex taskIndex,
              RepoFactory repoFactory,
              ContainerManager containerManager,
              Database database) {
            try {
              Task t = taskIndex.getTask(taskId);
              try (TaskCopy c =
                  usingTestingVersion ? t.acquireTestingCopy() : t.acquireRegisteredCopy()) {
                LOG.info("Initialising instance for repo " + repoId);
                int validityMinutes = validityMinutesInteger == null ? 60 : validityMinutesInteger;
                repoFactory.initialiseInstance(c, worker, database, repoId, validityMinutes);
              }
            } catch (TaskNotFoundException
                | RepoNotFoundException
                | RepoExpiredException
                | RepoStorageException e) {
              LOG.error("Failed to initialise repository", e);
              try {
                r.markError(database, e.getMessage());
              } catch (RepoStorageException e1) {
                LOG.error("Double fault trying to record repository error message", e1);
              }
              return Job.STATUS_FAILED;
            }
            return Job.STATUS_OK;
          }

          @Override
          public String getDescription() {
            return "Initialising repository";
          }
        });
    return r.toRepoInfoWithStatus();
  }

  @Override
  public RepoInfoWithStatus makeRepo(String taskId, Boolean usingTestingVersion,
                                     Integer validityMinutes, String variant, Integer seed)
      throws TaskNotFoundException, RepoNotFoundException,
          RetiredTaskException, RepoStorageException, TaskMissingVariantException {
    return makeRemoteRepo(
        taskId, usingTestingVersion, validityMinutes, variant, RepoInfo.REMOTE_UNSET, seed);
  }

  @Override
  public RepoInfoWithStatus getStatus(String repoId)
      throws RepoStorageException, RepoNotFoundException {
    return repoFactory.getInstanceIncludingCreating(repoId).toRepoInfoWithStatus();
  }

  @Override
  public List<String> listTags(String repoId) throws RepoStorageException, RepoNotFoundException {
    return repoFactory.getInstance(repoId).listTags();
  }

  @Override
  public List<String> listFiles(String repoId, String tag)
      throws RepoStorageException, RepoNotFoundException, RepoTagNotFoundException {
    return repoFactory.getInstance(repoId).listFiles(tag);
  }

  @Override
  public Response readFile(String repoId, String tag, String fileName)
      throws RepoStorageException, RepoFileNotFoundException, RepoNotFoundException,
          RepoTagNotFoundException {
    byte[] result = repoFactory.getInstance(repoId).readFile(tag, fileName);
    StreamingOutput s = output -> output.write(result);
    return Response.ok(s, MediaType.APPLICATION_OCTET_STREAM).build();
  }

  @Override
  public Response updateFile(String repoId, String tag, String fileName, FileData file)
      throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException,
          RepoNotFoundException {
    if (!Constants.HEAD.equals(tag)) {
      throw new RepoStorageException("Can only update files at HEAD revision");
    }
    repoFactory.getInstance(repoId).updateFile(fileName, file.getData());
    return Response.ok().entity("{\"message\":\"OK\"}").build();
  }

  @Override
  public Response deleteFile(String repoId, String tag, String fileName)
      throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException,
          RepoNotFoundException {
    if (!Constants.HEAD.equals(tag)) {
      throw new RepoFileNotFoundException("Can only delete files at HEAD revision");
    }
    repoFactory.getInstance(repoId).deleteFile(fileName);
    return Response.ok().entity("{\"message\":\"Deleted file\"}").build();
  }

  @Override
  public Response reset(String repoId, String tag)
      throws RepoStorageException, RepoExpiredException, RepoTagNotFoundException,
          RepoNotFoundException {
    repoFactory.getInstance(repoId).reset(tag);
    return Response.ok().entity("{\"message\":\"OK\"}").build();
  }

  @Override
  public RepoTag tag(String repoId)
      throws RepoStorageException, RepoExpiredException, RepoNotFoundException {
    RepoTag r = new RepoTag();
    r.setTag(repoFactory.getInstance(repoId).createNewTag());
    return r;
  }
}
