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
	
	public static Task loadTask(File taskSpecFile) throws IOException {
		ObjectMapper o = new ObjectMapper();
		return o.readValue(taskSpecFile, Task.class);
	}
	
	public Task getTask(String taskId) {
		return tasks.get(taskId);
	}

	public Collection<Task> getAllTasks() {
		return tasks.values();
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
	
	
	public static void create() {
		
		
		
		
		// generate a task id
		// create a directory to hold the task
		// init the git repo
		// create: /compile, /harness, /validator, /skeleton, /solution directories
		// create compile-solution.sh, run-harness.sh, run-validator.sh
		// for java tasks create a pom file
		// keep templates for these files in the project resources
		
	}
}
