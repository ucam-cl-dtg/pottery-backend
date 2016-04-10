package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import uk.ac.cam.cl.dtg.teaching.pottery.Criterion;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.managers.TaskManager;
import uk.ac.cam.cl.dtg.teaching.pottery.managers.TaskRegistrationException;

@Produces("application/json")
@Path("/tasks")
@Api(value = "/tasks", description = "Manages the descriptions of the programming questions.",position=0)
public class TasksController {

	public static final Logger log = LoggerFactory.getLogger(TasksController.class);
	private TaskManager taskManager;
		
	@Inject
	public TasksController(TaskManager taskManager) {
		super();
		this.taskManager = taskManager;
	}
	
	@GET
	@Path("/retired")
	@ApiOperation(value="Lists all retired tasks",response=Task.class,responseContainer="List",position=0)
	public Collection<Task> listRetired() {
		return taskManager.getRetiredTasks();
	}	
	
	@GET
	@Path("/released")
	@ApiOperation(value="Lists all released tasks",response=Task.class,responseContainer="List",position=0)
	public Collection<Task> listReleased() {
		return taskManager.getReleasedTasks();
	}

	@GET
	@Path("/")
	@ApiOperation(value="Lists all defined tasks",response=Task.class,responseContainer="List",position=0)
	public Collection<Task> listDefined() {
		return taskManager.getDefinedTasks();
	}

	@POST
	@Path("/create")
	@ApiOperation(value="Create a new task",response=Task.class)
	public Task create() throws IOException {
		return taskManager.createNewTask();
	}
	
	@GET
	@Path("/{taskId}")
	@ApiOperation(value="Returns information about a specific task",response=Task.class)
	public Task getTask(@PathParam("taskId") String taskID) {
		return taskManager.getTask(taskID);
	}
	
	@POST
	@Path("/{taskId}/retire")
	@ApiOperation(value="Retires a task and removes it from the list of available ones. This doesn't delete it (because people might have tried solving it).",response=Response.class)
	public Response retireTask(@PathParam("taskId") String taskID) {
		return null;
	}
	
	@POST
	@Path("/{taskId}/release")
	@ApiOperation(value="Releases (or updates the released version) of a task. If sha1 is not specified then HEAD is used.",response=Response.class)
	public Task releaseTask(@PathParam("taskId") String taskID, @FormParam("sha1") String sha1) throws TaskRegistrationException {
		return taskManager.registerTask(taskID, sha1);
	}
	
	// TODO: we should be able to remove this and replace it with a hook in the gitservlet
	@POST
	@Path("/{taskId}/test")
	@ApiOperation(value="Update the checkout of the testing version of the task to HEAD.",response=Response.class)
	public Response testTask(@PathParam("taskId") String taskID) {
		return null;
	}
	
	
	@GET
	@Path("/types")
	@ApiOperation(value="Lists defined task types and their description",position=1)
	public Map<String,String> listTypes() {
		return ImmutableMap.<String,String>builder()
				.put(Task.TYPE_ALGORITHM,"Algorithms & Data Structures: Tests the ability of the developer to compose algorithms and design appropriate data structures to solve the problem set out in the test.")
				.put(Task.TYPE_BLACKBOX,"Black Box Testing: Tests the developer's ability to test a solution without knowing or being able to review the underlying source code.")
				.put(Task.TYPE_DEBUGGING,"Debugging: Assesses a developers ability in debugging existing code that has a series of known issues that must be fixed for it to function correctly.")
				.put(Task.TYPE_DESIGN,"Design Approaches: Evaluates the logical flow of solution code and appropriate design features of the solution (e.g. ???)")
				.put(Task.TYPE_IO,"I/O Management: Evaluates the developers ability to implement strategies that result in appropriate I/O activity in a test solution.")
				.put(Task.TYPE_LIBRARY,"Using Existing APIs & Libraries:	Test the ability of a developer to appropriately exploit existing libraries and APIs to achieve the required test solution.")
				.put(Task.TYPE_MEMORY,"Cache & Memory Management: Evaluates the developers ability to implement strategies that result in appropriate cache and memory usage approaches in a test solution.")
				.put(Task.TYPE_UNITTEST,"Unit Testing: Assesses the ability of the developer to write unit tests on pre-existing source code and or source code that they have themselves written.")
				.build();
	}

	@GET
	@Path("/criteria")
	@ApiOperation(value="Lists defined criteria and their description",position=2)
	public List<Criterion> listCriteria() {
		try {
			return ImmutableList.<Criterion>builder()
					.add(new Criterion("compute"))
					.add(new Criterion("complexity"))
					.add(new Criterion("runningtime"))
					.add(new Criterion("mempeak"))
					.add(new Criterion("memtotal"))
					.add(new Criterion("cache"))
					.add(new Criterion("iototal"))
					.add(new Criterion("correctness"))
					.add(new Criterion("robustness"))
					.add(new Criterion("testability"))
					.add(new Criterion("testcoverage"))
					.build();
		} catch (CriterionNotFoundException e) {
			throw new Error(e);
		}
	}
	
	@GET
	@Path("/languages")
	@ApiOperation(value="Lists the available programming languages",position=3)
	public List<String>	listLanguages() {
		return ImmutableList.of("java");
	}	
}
