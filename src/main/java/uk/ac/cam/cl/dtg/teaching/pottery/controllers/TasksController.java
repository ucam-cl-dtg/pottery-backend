package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.util.Collection;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.SourceManager;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.Task;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

@Produces("application/json")
@Path("/tasks")
@Api(value = "/tasks", description = "Manages the descriptions of the programming questions.",position=0)
public class TasksController {

	private static final Logger log = LoggerFactory.getLogger(TasksController.class);
	
	@Inject
	Store store;
	
	@Inject
	SourceManager repoManager;
	
	@GET
	@Path("/")
	@ApiOperation(value="Lists all available tasks",response=Task.class,responseContainer="List",position=0)
	public Collection<Task> listAllTasks() {
		return store.tasks.values();
	}
	
	@POST
	@Path("/")
	public void createTask() {
		
	}
	
	@POST
	@Path("/{taskId}")
	public void createOrUpdateTask(@PathParam("taskId") String taskId) {
		// could store task as a git repo
		// special file to contain metadata
		// another file for problem briefing
		// skeleton code
		// source code for tests and validator
		// build script for tests and validator
		// testscript of some form
		// model answer
	}
}
