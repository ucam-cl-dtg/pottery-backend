package uk.ac.cam.cl.dtg.teaching.pottery.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;

@Singleton
public class TaskManager {

	private File checkoutLocation;

	private Map<String,Task> tasks = new HashMap<String,Task>();

	private static final Logger log = LoggerFactory.getLogger(TaskManager.class);
	
	@Inject
	public TaskManager(Config config) {
		checkoutLocation = config.getRegisteredTaskRoot();

		ObjectMapper o = new ObjectMapper();
		for(File f : checkoutLocation.listFiles()) {
			if (f.getName().startsWith(".")) continue;			
			File taskSpecFile = new File(f,"task.json");
			if (taskSpecFile.exists()) {
				try {
					Task t = o.readValue(taskSpecFile,Task.class);
					tasks.put(t.getTaskId(), t);
				} catch (IOException e) {
					log.warn("Failed to load task "+taskSpecFile,e);
				}
			}
			else {
				log.warn("Failed to find task.json file for task in {}",taskSpecFile);
			}
		}
	}
	
	/**
	 * Load a task from its spec file
	 * 
	 * @param taskId The id of the task
	 * @param active Whether the task is active or not
	 * @param taskSpecFile The file containing the spec for the task
	 * @return a task object with the task information in it
	 */
	public static Task loadTask(String taskId, boolean active, File taskSpecFile) {
		try {
			ObjectMapper o = new ObjectMapper();
			Task t = o.readValue(taskSpecFile, Task.class);
			t.setTaskId(taskId);
			t.setActive(active);
			return t;
		} catch (IOException e) {
			log.warn("Failed to load task "+taskSpecFile,e);
			return null;
		}
	}
	
	public Task getTask(String taskId) {
		return tasks.get(taskId);
	}

	public Collection<Task> getAllTasks(boolean includeDisabledTasks) {
		List<Task> result = new LinkedList<Task>();
		for(Task t : tasks.values()) {
			if (t.isActive() || includeDisabledTasks) {
				result.add(t);
			}
		}
		return result;
	}
	
	/**
	 * Return the directory for this task that contains the skeleton or template code for the task
	 * @param taskId
	 * @return
	 */
	public File getSkeletonDirectory(String taskId) {
		return new File(new File(checkoutLocation,taskId),"skeleton");
	}

	/** 
	 * Return the directory that contains the compilation script for this task
	 * @param taskId
	 * @return
	 */
	public File getCompileDirectory(String taskId) {
		return new File(new File(checkoutLocation,taskId),"compile");
	}

	public File getHarnessDirectory(String taskId) {
		return new File(new File(checkoutLocation,taskId),"harness");
	}

	public File getValidatorDirectory(String taskId) {
		return new File(new File(checkoutLocation,taskId),"validator");
	}
}
