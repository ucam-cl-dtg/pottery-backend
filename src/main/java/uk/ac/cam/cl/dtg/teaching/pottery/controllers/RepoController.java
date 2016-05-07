package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
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

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import uk.ac.cam.cl.dtg.teaching.pottery.dto.FileData;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.RepoTag;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.managers.RepoManager;
import uk.ac.cam.cl.dtg.teaching.pottery.managers.TaskManager;


@Produces("application/json")
@Path("/repo")
@Api(value = "/repo", description = "Manages the candidates attempt at the task",position=1)
public class RepoController {
	
	private RepoManager sourceManager;
	private TaskManager taskManager;
	
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(RepoController.class);

	@Inject
	public RepoController(RepoManager repoManager, TaskManager taskManager) {
		super();
		this.sourceManager = repoManager;
		this.taskManager = taskManager;
	}

	@POST
	@Path("/")
	@ApiOperation(value="Start a new repository",
		notes="Starts a new repository for solving the specified task",position=0)
	public Repo makeRepo(@FormParam("taskId") String taskId) throws TaskNotFoundException, RepoException, IOException, TaskNotAvailableException {
		Task t = taskManager.getReleasedTask(taskId);
		if (t == null) throw new TaskNotFoundException();
		if (t.isLocked()) throw new TaskNotAvailableException();
		
		Repo r = sourceManager.createRepo(t);		
		sourceManager.copyFiles(r.getRepoId(), taskManager.getSkeletonRoot(t));
		return r;
	}
	
	@GET
	@Path("/{repoId}")
	@ApiOperation(value="List all the tags in repository",response=String.class,responseContainer="List")
	public List<String> listTags(@PathParam("repoId") String repoId) throws RepoException, IOException {
		return sourceManager.listTags(repoId);
	}
	
	@GET 
	@Path("/{repoId}/{tag}")
	@ApiOperation(value="List all the files in the repository",response=String.class,responseContainer="List",position=1)
	public List<String> listFiles(@PathParam("repoId") String repoId, @PathParam("tag") String tag) throws RepoException, IOException {
		return sourceManager.listFiles(repoId, tag);
	}
	
	@GET
	@Path("/{repoId}/{tag}/{fileName:.+}")
	@Produces("application/octet-stream")
	@ApiOperation(value="Read a file from the repository",
			notes="Returns the file contents directly",position=2)
	public Response readFile(@PathParam("repoId") String repoId, @PathParam("tag") String tag, @PathParam("fileName") String fileName) throws IOException, RepoException {
		StreamingOutput s = sourceManager.readFile(repoId, tag, fileName);
		return Response.ok(s,MediaType.APPLICATION_OCTET_STREAM).build();
	}
	
	
	@POST
	@Consumes("multipart/form-data")
	@Path("/{repoId}/{tag}/{fileName:.+}")
	@ApiOperation(value="Update a file in the repository",
			notes="The new contents of the file should be submitted as a multipart form request",position=3)
	public Response updateFile(@PathParam("repoId") String repoId, @PathParam("tag") String tag, @PathParam("fileName") String fileName, @MultipartForm FileData file) throws RepoException, IOException {
		if (!sourceManager.getHeadTag().equals(tag)) {
			throw new RepoException("Can only update files at HEAD revision");
		}
		sourceManager.updateFile(repoId,fileName,file.getData());
		return Response.ok().entity("{\"message\":\"OK\"}").build();
	}
	
	@DELETE
	@Path("/{repoId}/{tag}/{fileName:.+}")
	@ApiOperation(value="Delete a file from the repository",position=4)
	public Response deleteFile(@PathParam("repoId") String repoId, @PathParam("tag") String tag, @PathParam("fileName") String fileName) throws IOException, RepoException {
		if (!sourceManager.getHeadTag().equals(tag)) {
			throw new RepoException("Can only delete files at HEAD revision");
		}
		sourceManager.deleteFile(repoId,fileName);
		return Response.ok().entity("{\"message\":\"OK\"}").build();
	}
	
	@POST
	@Path("/{repoId}/reset/{tag}")
	@ApiOperation(value="Set the contents of the repository to be what it was at this particular tag",position=5)
	public Response reset(@PathParam("repoId") String repoId, @PathParam("tag") String tag) throws IOException, RepoException {
		sourceManager.reset(repoId, tag);
		return Response.ok().entity("{\"message\":\"OK\"}").build();
	}
	
	@POST
	@Path("/{repoId}")
	@ApiOperation(value="Create a tag in the repository",
		notes="Submissions (for testing) are created with reference to tags in the repository")
	public RepoTag tag(@PathParam("repoId") String repoId) throws IOException, RepoException {
		RepoTag r = new RepoTag();
		r.setTag(sourceManager.newTag(repoId));
		return r;
	}
	
		
}
