package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskRegistrationException;

@Singleton
public class TaskManager {

	public static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);
			
	private Map<String,Task> definedTasks;
	private Map<String,Task> retiredTasks;
	private Map<String,Task> registeredTasks;

	private TaskFactory taskFactory;
	private ContainerManager containerManager;
	private Database database;
	
	@Inject
	public TaskManager(ContainerManager containerManager, Database database, TaskFactory taskFactory) throws GitAPIException, IOException, SQLException, TaskException {
		this.containerManager = containerManager;
		
		this.taskFactory = taskFactory;
		this.database = database;
		this.definedTasks = new HashMap<>();
		this.registeredTasks = new HashMap<>();
		this.retiredTasks = new HashMap<>();
		
		List<String> taskIds;
		try(TransactionQueryRunner q = database.getQueryRunner()) {
			taskIds = TaskDefInfo.getAllTaskIds(q);
		}
		
		for(String taskId : taskIds) {
			try {
				Task t = taskFactory.getInstance(taskId);
				definedTasks.put(taskId, t);
				if (t.isRetired()) {
					retiredTasks.put(taskId,t);
				}
				if (t.getRegisteredClone() != null) {
					registeredTasks.put(taskId, t);
				}
			}
			catch (TaskException e) {
				LOG.warn("Ignoring task "+taskId,e);
			}
		}
	}
	
	public TaskInfo createNewTask() throws TaskException {
		Task newTask = taskFactory.createInstance();
		definedTasks.put(newTask.getTaskId(), newTask);
		return newTask.getTestingClone().getInfo();
	}
	
	/**
	 * Register this particular version of the task for others to use.
	 * 
	 * This works by cloning (if necessary) the task bare repo and then resetting it to the desired sha1. Re-registering a task just changes the version that is checked out.
	 *  
	 *  @param taskId is the uuid of the task to register
	 *  @param sha1 is the version of the commit to register
	 * @throws TaskRegistrationException 
	 * @throws IOException 
	 * @throws TaskCloneException 
	 * @throws TaskException 
	 * @throws SQLException 
	 */
	public TaskInfo registerTask(String taskId, String sha1) throws TaskRegistrationException, TaskException, TaskCloneException, IOException, SQLException {
		Task task = definedTasks.get(taskId);
		task.registerTask(sha1, containerManager,database);
		registeredTasks.put(taskId, task);
		return task.getRegisteredClone().getInfo();
	}
	
	public Collection<TaskInfo> getTestingTasks() {
		return definedTasks.values().stream().map(t -> t.getTestingClone().getInfo()).collect(Collectors.toList());
	}
	
	public Collection<TaskInfo> getRegisteredTasks() {
		return registeredTasks.values().stream().map(t -> t.getRegisteredClone().getInfo()).collect(Collectors.toList());
	}

	public Collection<TaskInfo> getRetiredTasks() {
		return retiredTasks.values().stream().map(t -> t.getTestingClone().getInfo()).collect(Collectors.toList());
	}

	public TaskInfo getTestingTask(String taskId) {
		return definedTasks.get(taskId).getTestingClone().getInfo();
	}

	public TaskInfo getRegisteredTaskInfo(String taskId) {
		return registeredTasks.get(taskId).getRegisteredClone().getInfo();
	}

	public Task getTask(String taskId) {
		return definedTasks.get(taskId);
	}
	
	public void updateTesting(String taskID) throws TaskCloneException {
		definedTasks.get(taskID).getTestingClone().update(Constants.HEAD);
	}

}
