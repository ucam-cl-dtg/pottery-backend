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
package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;

@Singleton
public class TaskManager {

	protected static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);
			
	private Map<String,Task> definedTasks;

	private TaskFactory taskFactory;
	
	@Inject
	public TaskManager(ContainerManager containerManager, TaskFactory taskFactory, Database database) throws TaskStorageException {
		this.taskFactory = taskFactory;
		this.definedTasks = new ConcurrentHashMap<>();
		
		List<String> taskIds;
		try(TransactionQueryRunner q = database.getQueryRunner()) {
			taskIds = TaskDefInfo.getAllTaskIds(q);
		} catch (SQLException e1) {
			throw new TaskStorageException("Failed to load list of tasks from database",e1);
		}
		
		for(String taskId : taskIds) {
			try {
				Task t = taskFactory.getInstance(taskId);
				definedTasks.put(taskId, t);
			}
			catch (TaskNotFoundException|InvalidTaskSpecificationException|TaskStorageException e) {
				LOG.warn("Ignoring task "+taskId,e);
			}
		}
	}
	
	public Collection<TaskInfo> getTestingTasks() {
		List<TaskInfo> r = new LinkedList<>();
		for (Task t : definedTasks.values()) {
			if (!t.isRetired()) {
				try(TaskCopy c = t.acquireTestingCopy()) {
					if (c != null) {
						r.add(c.getInfo());
					}
				}
			}
		}
		return r;
	}
	
	public Collection<TaskInfo> getRegisteredTasks() {
		List<TaskInfo> r = new LinkedList<>();
		for (Task t : definedTasks.values()) {
			if (!t.isRetired()) {
				try(TaskCopy c = t.acquireRegisteredCopy()) {
					if (c != null) {
						r.add(c.getInfo());
					}
				}
			}
		}
		return r;
	}
	
	public TaskInfo getTestingTaskInfo(String taskId) throws TaskNotFoundException {
		try (TaskCopy t = definedTasks.get(taskId).acquireTestingCopy()) {
			if (t == null) throw new TaskNotFoundException("Failed to find a testing task with ID "+taskId);
			return t.getInfo();
		}
	}

	public TaskInfo getRegisteredTaskInfo(String taskId) throws TaskNotFoundException {
		try (TaskCopy t = definedTasks.get(taskId).acquireRegisteredCopy()) {
			if (t == null) throw new TaskNotFoundException("Failed to find a registered task with ID "+taskId);
			return t.getInfo();
		}
	}

	public Task getTask(String taskId) throws TaskNotFoundException {
		Task t = definedTasks.get(taskId);
		if (t == null) throw new TaskNotFoundException("Failed to find task "+taskId);
		return t;
	}

	public Collection<String> getAllTasks() {
		return definedTasks.values().stream().filter(t -> !t.isRetired()).map(t->t.getTaskId()).collect(Collectors.toList());
	}
	
	public Collection<String> getRetiredTasks() {
		return definedTasks.values().stream().filter(t -> t.isRetired()).map(t->t.getTaskId()).collect(Collectors.toList());
	}

	public void add(Task newTask) {
		definedTasks.put(newTask.getTaskId(), newTask);
	}
}
