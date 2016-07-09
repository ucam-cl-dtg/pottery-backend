package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;

@Singleton
public class TaskManager {

	protected static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);
			
	private Map<String,Task> definedTasks;

	private TaskFactory taskFactory;
	
	@Inject
	public TaskManager(ContainerManager containerManager, TaskFactory taskFactory, Database database) throws GitAPIException, IOException, SQLException, TaskException {
		this.taskFactory = taskFactory;
		this.definedTasks = new HashMap<>();
		
		List<String> taskIds;
		try(TransactionQueryRunner q = database.getQueryRunner()) {
			taskIds = TaskDefInfo.getAllTaskIds(q);
		}
		
		for(String taskId : taskIds) {
			try {
				Task t = taskFactory.getInstance(taskId);
				definedTasks.put(taskId, t);
			}
			catch (TaskException e) {
				LOG.warn("Ignoring task "+taskId,e);
			}
		}
	}
	
	public TaskInfo createNewTask() throws TaskException {
		Task newTask = taskFactory.createInstance();
		definedTasks.put(newTask.getTaskId(), newTask);
		return new TaskInfo(newTask.getTaskId());
	}
	
	public Collection<TaskInfo> getTestingTasks() {
		return definedTasks.values().stream()
				.filter(t -> t.getTestingCopy() != null)
				.map(t -> t.getTestingCopy().getInfo())
				.collect(Collectors.toList());
	}
	
	public Collection<TaskInfo> getRegisteredTasks() {
		return definedTasks.values().stream()
				.filter(t -> t.getRegisteredCopy() != null)
				.map(t -> t.getRegisteredCopy().getInfo())
				.collect(Collectors.toList());
	}

	public TaskInfo getTestingTaskInfo(String taskId) throws TaskNotFoundException {
		TaskCopy t = definedTasks.get(taskId).getTestingCopy();
		if (t == null) throw new TaskNotFoundException("Failed to find a testing task with ID "+taskId);
		return t.getInfo();
	}

	public TaskInfo getRegisteredTaskInfo(String taskId) throws TaskNotFoundException {
		TaskCopy t = definedTasks.get(taskId).getRegisteredCopy();
		if (t == null) throw new TaskNotFoundException("Failed to find a registered task with ID "+taskId);
		return t.getInfo();
	}

	public Task getTask(String taskId) {
		return definedTasks.get(taskId);
	}

	public Collection<String> getAllTasks() {
		return definedTasks.keySet();
	}
}
