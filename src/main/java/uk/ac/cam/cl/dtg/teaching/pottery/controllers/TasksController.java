/**
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

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import uk.ac.cam.cl.dtg.teaching.pottery.Criterion;
import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.task.BuilderInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@Produces("application/json")
@Path("/tasks")
@Api(value = "/tasks", description = "Manages the descriptions of the programming questions.",position=0)
public class TasksController {

	protected static final Logger LOG = LoggerFactory.getLogger(TasksController.class);

	private TaskFactory taskFactory;
	
	private TaskIndex taskIndex;
	
	private Worker worker;

	private Database database;
	
	@Inject
	public TasksController(TaskFactory taskFactory, TaskIndex taskIndex, Worker worker, Database database) {
		super();
		this.taskFactory = taskFactory;
		this.taskIndex = taskIndex;
		this.worker = worker;
		this.database = database;
	}
		
	@GET
	@Path("/registered")
	@ApiOperation(value="Lists all registered tasks",response=TaskInfo.class,responseContainer="List",position=0)
	public Collection<TaskInfo> listRegistered() {
		return taskIndex.getRegisteredTasks();
	}

	@GET
	@Path("/")
	@ApiOperation(value="List the ids of all tasks (not retired) that exist",response=String.class,responseContainer="List")
	public Collection<String> listAll() {
		return taskIndex.getAllTasks();
	}

	@GET
	@Path("/retired")
	@ApiOperation(value="List the ids of all retired tasks",response=String.class,responseContainer="List")
	public Collection<String> listRetired() {
		return taskIndex.getRetiredTasks();
	}
	
	@GET
	@Path("/testing")
	@ApiOperation(value="Lists all tasks with a testing version",response=TaskInfo.class,responseContainer="List",position=0)
	public Collection<TaskInfo> listTesting() {
		return taskIndex.getTestingTasks();
	}

	@POST
	@Path("/create")
	@ApiOperation(value="Create a new task",response=TaskInfo.class)
	public Response create(@Context UriInfo uriInfo) throws TaskStorageException {
		Task newTask = taskFactory.createInstance();
		taskIndex.add(newTask);
		String uri = uriInfo.getBaseUri().toString();
		uri = uri.replaceAll("/api/", "/git/"+newTask.getTaskId());
		return Response.ok().entity(String.format("{\"taskId\":\"%s\",\"remote\":\"%s\"}",newTask.getTaskId(),uri)).build();
	}
	
	@GET
	@Path("/{taskId}")
	@ApiOperation(value="Returns information about a specific task",response=TaskInfo.class)
	public TaskInfo getTask(@PathParam("taskId") String taskID) throws TaskNotFoundException {
		return taskIndex.getTestingTaskInfo(taskID);
	}
	
	@POST
	@Path("/{taskId}/retire")
	@ApiOperation(value="Mark a task as retired",response=TaskInfo.class)
	public Response retireTask(@PathParam("taskId") String taskID) throws TaskNotFoundException, TaskStorageException, RetiredTaskException {
		taskIndex.getTask(taskID).setRetired(database);
		return Response.ok().entity("{\"message\":\"OK\"}").build();
	}
	
	@POST
	@Path("/{taskId}/register")
	@ApiOperation(value="Registers (or updates the registered version) of a task. If sha1 is not specified then HEAD is used.")
	public BuilderInfo scheduleTaskRegistration(@PathParam("taskId") String taskID, @FormParam("sha1") String sha1) throws TaskNotFoundException, RetiredTaskException {
		return taskIndex.getTask(taskID).scheduleBuildRegisteredCopy(sha1, worker);
	}
	
	@GET
	@Path("/{taskId}/registering_status")
	@ApiOperation(value="Polls the progress of the current registration process.")
	public BuilderInfo pollTaskRegistraionStatus(@PathParam("taskId") String taskID) throws TaskNotFoundException {
		return taskIndex.getTask(taskID).getRegisteredCopyBuilderInfo();
	}
	
	@POST
	@Path("/{taskId}/update")
	@ApiOperation(value="Registers (or updates the testing version) of a task.")
	public BuilderInfo scheduleTaskTesting(@PathParam("taskId") String taskID) throws TaskNotFoundException, RetiredTaskException {
		return taskIndex.getTask(taskID).scheduleBuildTestingCopy(worker);
	}
	
	@GET
	@Path("/{taskId}/update_status")
	@ApiOperation(value="Polls the progress of the current testing registration process.")
	public BuilderInfo pollTaskTestingStatus(@PathParam("taskId") String taskID) throws TaskNotFoundException {
		return taskIndex.getTask(taskID).getTestingCopyBuilderInfo();
	}
	
	
	@GET
	@Path("/types")
	@ApiOperation(value="Lists defined task types and their description",position=1)
	public Map<String,String> listTypes() {
		return ImmutableMap.<String,String>builder()
				.put(TaskInfo.TYPE_ALGORITHM,"Algorithms & Data Structures: Tests the ability of the developer to compose algorithms and design appropriate data structures to solve the problem set out in the test.")
				.put(TaskInfo.TYPE_BLACKBOX,"Black Box Testing: Tests the developer's ability to test a solution without knowing or being able to review the underlying source code.")
				.put(TaskInfo.TYPE_DEBUGGING,"Debugging: Assesses a developers ability in debugging existing code that has a series of known issues that must be fixed for it to function correctly.")
				.put(TaskInfo.TYPE_DESIGN,"Design Approaches: Evaluates the logical flow of solution code and appropriate design features of the solution (e.g. ???)")
				.put(TaskInfo.TYPE_IO,"I/O Management: Evaluates the developers ability to implement strategies that result in appropriate I/O activity in a test solution.")
				.put(TaskInfo.TYPE_LIBRARY,"Using Existing APIs & Libraries:	Test the ability of a developer to appropriately exploit existing libraries and APIs to achieve the required test solution.")
				.put(TaskInfo.TYPE_MEMORY,"Cache & Memory Management: Evaluates the developers ability to implement strategies that result in appropriate cache and memory usage approaches in a test solution.")
				.put(TaskInfo.TYPE_UNITTEST,"Unit Testing: Assesses the ability of the developer to write unit tests on pre-existing source code and or source code that they have themselves written.")
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
