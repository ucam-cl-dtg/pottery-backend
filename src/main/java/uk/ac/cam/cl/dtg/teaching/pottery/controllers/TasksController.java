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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.BuilderInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Criterion;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskLocation;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class TasksController implements uk.ac.cam.cl.dtg.teaching.pottery.api.TasksController {

  protected static final Logger LOG = LoggerFactory.getLogger(TasksController.class);

  private TaskFactory taskFactory;

  private TaskIndex taskIndex;

  private Worker worker;

  private Database database;

  @Inject
  public TasksController(
      TaskFactory taskFactory, TaskIndex taskIndex, Worker worker, Database database) {
    super();
    this.taskFactory = taskFactory;
    this.taskIndex = taskIndex;
    this.worker = worker;
    this.database = database;
  }

  @Override
  public Collection<TaskInfo> listRegistered() {
    return taskIndex.getRegisteredTasks();
  }

  @Override
  public Collection<String> listAll() {
    return taskIndex.getAllTasks();
  }

  @Override
  public Collection<String> listRetired() {
    return taskIndex.getRetiredTasks();
  }

  @Override
  public Collection<TaskInfo> listTesting() {
    return taskIndex.getTestingTasks();
  }

  @Override
  public TaskLocation create(@Context UriInfo uriInfo) throws TaskStorageException {
    Task newTask = taskFactory.createInstance();
    taskIndex.add(newTask);
    String uri = uriInfo.getBaseUri().toString();
    uri = uri.replaceAll("/api/", "/git/" + newTask.getTaskId());
    return new TaskLocation(newTask.getTaskId(), uri);
  }

  }

  @Override
  public TaskInfo getTask(String taskId) throws TaskNotFoundException {
    return taskIndex.getTestingTaskInfo(taskId);
  }

  @Override
  public Response retireTask(String taskId)
      throws TaskNotFoundException, TaskStorageException, RetiredTaskException {
    taskIndex.getTask(taskId).setRetired(database);
    return Response.ok().entity("{\"message\":\"OK\"}").build();
  }

  @Override
  public BuilderInfo scheduleTaskRegistration(String taskId, String sha1)
      throws TaskNotFoundException, RetiredTaskException {
    return taskIndex.getTask(taskId).scheduleBuildRegisteredCopy(sha1, worker);
  }

  @Override
  public BuilderInfo pollTaskRegistrationStatus(String taskId) throws TaskNotFoundException {
    return taskIndex.getTask(taskId).getRegisteredCopyBuilderInfo();
  }

  @Override
  public BuilderInfo scheduleTaskTesting(String taskId)
      throws TaskNotFoundException, RetiredTaskException {
    return taskIndex.getTask(taskId).scheduleBuildTestingCopy(worker);
  }

  @Override
  public BuilderInfo pollTaskTestingStatus(String taskId) throws TaskNotFoundException {
    return taskIndex.getTask(taskId).getTestingCopyBuilderInfo();
  }

  @Override
  public Map<String, String> listTypes() {
    return ImmutableMap.<String, String>builder()
        .put(
            TaskInfo.TYPE_ALGORITHM,
            "Algorithms & Data Structures: Tests the ability of the developer to compose "
                + "algorithms and design appropriate data structures to solve the problem set out "
                + "in the test.")
        .put(
            TaskInfo.TYPE_BLACKBOX,
            "Black Box Testing: Tests the developer's ability to test a solution without knowing "
                + "or being able to review the underlying source code.")
        .put(
            TaskInfo.TYPE_DEBUGGING,
            "Debugging: Assesses a developers ability in debugging existing code that has a series "
                + "of known issues that must be fixed for it to function correctly.")
        .put(
            TaskInfo.TYPE_DESIGN,
            "Design Approaches: Evaluates the logical flow of solution code and appropriate "
                + "design features of the solution (e.g. ???)")
        .put(
            TaskInfo.TYPE_IO,
            "I/O Management: Evaluates the developers ability to implement strategies that result "
                + "in appropriate I/O activity in a test solution.")
        .put(
            TaskInfo.TYPE_LIBRARY,
            "Using Existing APIs & Libraries: Test the ability of a developer to appropriately "
                + "exploit existing libraries and APIs to achieve the required test solution.")
        .put(
            TaskInfo.TYPE_MEMORY,
            "Cache & Memory Management: Evaluates the developers ability to implement strategies "
                + "that result in appropriate cache and memory usage approaches in a test "
                + "solution.")
        .put(
            TaskInfo.TYPE_UNITTEST,
            "Unit Testing: Assesses the ability of the developer to write unit tests on "
                + "pre-existing source code and or source code that they have themselves written.")
        .build();
  }

  @Override
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

  @Override
  public List<String> listLanguages() {
    return ImmutableList.of("java");
  }
}
