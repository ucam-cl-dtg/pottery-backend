package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
import java.util.Collection;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.ApiOperation;

import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskRepo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskRepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskTestResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskRepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.managers.TaskRepoManager;

@Produces("application/json")
@Path("/taskrepos")
public class TaskRepoController {

	private TaskRepoManager taskRepoManager;
	
	@Inject
	public TaskRepoController(TaskRepoManager taskRepoManager) {
		this.taskRepoManager = taskRepoManager;
	}
	
	@POST
	@Path("/")
	@ApiOperation(value="Create a new task repository",response=TaskRepo.class)
	public TaskRepo create() throws TaskRepoException {
		try {
			return taskRepoManager.create();
		} catch (IOException e) {
			throw new TaskRepoException("Failed to create task repository",e);
		}
	}
	
	@GET
	@Path("/")
	@ApiOperation(value="Return a list of task-repos",response=TaskRepoInfo.class,responseContainer="List")
	public Collection<TaskRepoInfo> list() {
		return taskRepoManager.list();
	}
	
	@POST
	@Path("/register/{taskRepoId}")
	@ApiOperation(value="Register a new task based on the particular SHA1 from the task repo",response=Task.class)
	public Task register(@PathParam("taskRepoId") String taskRepoId, @FormParam("sha1") String sha1) throws IOException {
		return taskRepoManager.register(taskRepoId,sha1);
	}
	
	@POST
	@Path("/test/{taskRepoId}")
	@ApiOperation(value="Check that the task repository at this SHA1 actually conforms to the specification", response=TaskTestResponse.class)
	public TaskTestResponse test(@PathParam("taskRepoId") String taskRepoId, @FormParam("sha1") String sha1) throws IOException, GitAPIException {
		return taskRepoManager.test(taskRepoId, sha1);
	}
}

