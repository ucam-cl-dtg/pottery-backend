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

package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.eclipse.jgit.lib.Constants;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.FileData;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.RepoTag;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoFileNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoTagNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;

@Produces("application/json")
@Path("/repo")
@Api(value = "/repo", description = "Manages the candidates attempt at the task", position = 1)
public class RepoController {

  private RepoFactory repoFactory;
  private TaskIndex taskIndex;

  protected static final Logger LOG = LoggerFactory.getLogger(RepoController.class);

  @Inject
  public RepoController(RepoFactory repoFactory, TaskIndex taskIndex) {
    super();
    this.repoFactory = repoFactory;
    this.taskIndex = taskIndex;
  }

  @POST
  @Path("/")
  @ApiOperation(
    value = "Start a new repository",
    notes = "Starts a new repository for solving the specified task",
    position = 0
  )
  public RepoInfo makeRepo(
      @FormParam("taskId") String taskId,
      @FormParam("usingTestingVersion") Boolean usingTestingVersion,
      @FormParam("validityMinutes") Integer validityMinutes)
      throws TaskNotFoundException, RepoExpiredException, RepoStorageException,
          RetiredTaskException, RepoNotFoundException {
    if (taskId == null) {
      throw new TaskNotFoundException("No taskId specified");
    }
    if (usingTestingVersion == null) {
      usingTestingVersion = false;
    }
    if (validityMinutes == null) {
      validityMinutes = 60;
    }
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MINUTE, validityMinutes);
    Date expiryDate = cal.getTime();
    Task t = taskIndex.getTask(taskId);
    if (t.isRetired()) {
      throw new RetiredTaskException("Cannot start a new repository for task " + taskId);
    }
    try (TaskCopy c = usingTestingVersion ? t.acquireTestingCopy() : t.acquireRegisteredCopy()) {
      Repo r = repoFactory.createInstance(taskId, usingTestingVersion, expiryDate);
      r.copyFiles(c);
      return r.toRepoInfo();
    }
  }

  @GET
  @Path("/{repoId}")
  @ApiOperation(
    value = "List all the tags in repository",
    response = String.class,
    responseContainer = "List"
  )
  public List<String> listTags(@PathParam("repoId") String repoId)
      throws RepoStorageException, RepoNotFoundException {
    return repoFactory.getInstance(repoId).listTags();
  }

  @GET
  @Path("/{repoId}/{tag}")
  @ApiOperation(
    value = "List all the files in the repository",
    response = String.class,
    responseContainer = "List",
    position = 1
  )
  public List<String> listFiles(@PathParam("repoId") String repoId, @PathParam("tag") String tag)
      throws RepoStorageException, RepoNotFoundException, RepoTagNotFoundException {
    return repoFactory.getInstance(repoId).listFiles(tag);
  }

  @GET
  @Path("/{repoId}/{tag}/{fileName:.+}")
  @Produces("application/octet-stream")
  @ApiOperation(
    value = "Read a file from the repository",
    notes = "Returns the file contents directly",
    position = 2
  )
  public Response readFile(
      @PathParam("repoId") String repoId,
      @PathParam("tag") String tag,
      @PathParam("fileName") String fileName)
      throws RepoStorageException, RepoFileNotFoundException, RepoNotFoundException,
          RepoTagNotFoundException {
    StreamingOutput s = repoFactory.getInstance(repoId).readFile(tag, fileName);
    return Response.ok(s, MediaType.APPLICATION_OCTET_STREAM).build();
  }

  @POST
  @Consumes("multipart/form-data")
  @Path("/{repoId}/{tag}/{fileName:.+}")
  @ApiOperation(
    value = "Update (or create) a file in the repository",
    notes =
        "Any required directories will be created automatically. The new contents of the file "
            + "should be submitted as a multipart form request",
    position = 3
  )
  public Response updateFile(
      @PathParam("repoId") String repoId,
      @PathParam("tag") String tag,
      @PathParam("fileName") String fileName,
      @MultipartForm FileData file)
      throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException,
          RepoNotFoundException {
    if (!Constants.HEAD.equals(tag)) {
      throw new RepoStorageException("Can only update files at HEAD revision");
    }
    repoFactory.getInstance(repoId).updateFile(fileName, file.getData());
    return Response.ok().entity("{\"message\":\"OK\"}").build();
  }

  @DELETE
  @Path("/{repoId}/{tag}/{fileName:.+}")
  @ApiOperation(value = "Delete a file from the repository", position = 4)
  public Response deleteFile(
      @PathParam("repoId") String repoId,
      @PathParam("tag") String tag,
      @PathParam("fileName") String fileName)
      throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException,
          RepoNotFoundException {
    if (!Constants.HEAD.equals(tag)) {
      throw new RepoFileNotFoundException("Can only delete files at HEAD revision");
    }
    repoFactory.getInstance(repoId).deleteFile(fileName);
    return Response.ok().entity("{\"message\":\"Deleted file\"}").build();
  }

  @POST
  @Path("/{repoId}/reset/{tag}")
  @ApiOperation(
    value = "Set the contents of the repository to be what it was at this particular tag",
    position = 5
  )
  public Response reset(@PathParam("repoId") String repoId, @PathParam("tag") String tag)
      throws RepoStorageException, RepoExpiredException, RepoTagNotFoundException,
          RepoNotFoundException {
    repoFactory.getInstance(repoId).reset(tag);
    return Response.ok().entity("{\"message\":\"OK\"}").build();
  }

  @POST
  @Path("/{repoId}")
  @ApiOperation(
    value = "Create a tag in the repository",
    notes = "Submissions (for testing) are created with reference to tags in the repository"
  )
  public RepoTag tag(@PathParam("repoId") String repoId)
      throws RepoStorageException, RepoExpiredException, RepoNotFoundException {
    RepoTag r = new RepoTag();
    r.setTag(repoFactory.getInstance(repoId).createNewTag());
    return r;
  }
}
