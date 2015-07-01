package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class TaskManager {

	private File storageLocation;

	private Map<String,Task> tasks = new HashMap<String,Task>();

	private static final Logger log = LoggerFactory.getLogger(TaskManager.class);
	
	@Inject
	public TaskManager(Config config) {
		storageLocation = config.getStorageLocation();

		ObjectMapper o = new ObjectMapper();
		for(File f : storageLocation.listFiles()) {
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
	
	public Task getTask(String taskId) {
		return tasks.get(taskId);
	}

	public Collection<Task> getAllTasks() {
		return tasks.values();
	}
	
	public File getSkeletonLocation(String taskId) {
		return new File(new File(storageLocation,taskId),"skeleton");
	}

}
