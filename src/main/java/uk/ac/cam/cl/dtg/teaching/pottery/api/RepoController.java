package uk.ac.cam.cl.dtg.teaching.pottery.api;

import com.wordnik.swagger.annotations.ApiOperation;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import uk.ac.cam.cl.dtg.teaching.pottery.model.FileData;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoTag;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.*;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;

public interface RepoController {
    @POST
    @Path("/")
    @ApiOperation(
      value = "Start a new repository",
      notes = "Starts a new repository for solving the specified task",
      position = 0
    )
    RepoInfo makeRepo(
            @FormParam("taskId") String taskId,
            @FormParam("usingTestingVersion") Boolean usingTestingVersion,
            @FormParam("validityMinutes") Integer validityMinutes)
        throws TaskNotFoundException, RepoExpiredException, RepoStorageException,
            RetiredTaskException, RepoNotFoundException;

    @GET
    @Path("/{repoId}")
    @ApiOperation(
      value = "List all the tags in repository",
      response = String.class,
      responseContainer = "List"
    )
    List<String> listTags(@PathParam("repoId") String repoId)
        throws RepoStorageException, RepoNotFoundException;

    @GET
    @Path("/{repoId}/{tag}")
    @ApiOperation(
      value = "List all the files in the repository",
      response = String.class,
      responseContainer = "List",
      position = 1
    )
    List<String> listFiles(@PathParam("repoId") String repoId, @PathParam("tag") String tag)
        throws RepoStorageException, RepoNotFoundException, RepoTagNotFoundException;

    @GET
    @Path("/{repoId}/{tag}/{fileName:.+}")
    @Produces("application/octet-stream")
    @ApiOperation(
      value = "Read a file from the repository",
      notes = "Returns the file contents directly",
      position = 2
    )
    Response readFile(
            @PathParam("repoId") String repoId,
            @PathParam("tag") String tag,
            @PathParam("fileName") String fileName)
        throws RepoStorageException, RepoFileNotFoundException, RepoNotFoundException,
            RepoTagNotFoundException;

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
    Response updateFile(
            @PathParam("repoId") String repoId,
            @PathParam("tag") String tag,
            @PathParam("fileName") String fileName,
            @MultipartForm FileData file)
        throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException,
            RepoNotFoundException;

    @DELETE
    @Path("/{repoId}/{tag}/{fileName:.+}")
    @ApiOperation(value = "Delete a file from the repository", position = 4)
    Response deleteFile(
            @PathParam("repoId") String repoId,
            @PathParam("tag") String tag,
            @PathParam("fileName") String fileName)
        throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException,
            RepoNotFoundException;

    @POST
    @Path("/{repoId}/reset/{tag}")
    @ApiOperation(
      value = "Set the contents of the repository to be what it was at this particular tag",
      position = 5
    )
    Response reset(@PathParam("repoId") String repoId, @PathParam("tag") String tag)
        throws RepoStorageException, RepoExpiredException, RepoTagNotFoundException,
            RepoNotFoundException;

    @POST
    @Path("/{repoId}")
    @ApiOperation(
      value = "Create a tag in the repository",
      notes = "Submissions (for testing) are created with reference to tags in the repository"
    )
    RepoTag tag(@PathParam("repoId") String repoId)
        throws RepoStorageException, RepoExpiredException, RepoNotFoundException;
}
