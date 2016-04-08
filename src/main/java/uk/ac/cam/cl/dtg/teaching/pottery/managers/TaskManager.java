package uk.ac.cam.cl.dtg.teaching.pottery.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.app.RegistrationTag;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTagFormatException;

@Singleton
public class TaskManager {

	private File checkoutLocation;
	
	/**
	 * A map of taskId (uuid) to Task objects
	 */
	private Map<String,Task> tasks;

	/**
	 * A map of taskId (uiid) to the repository it came from
	 */
	private Map<String,RegistrationTag> taskRegistrations;
	
	private UUIDGenerator uuidGenerator;
	
	private static final Logger log = LoggerFactory.getLogger(TaskManager.class);
	
	
	@Inject
	public TaskManager(Config config) throws IOException, GitAPIException, InvalidTagFormatException {
		File taskReposDir = config.getTaskRepoRoot();

		this.checkoutLocation = config.getRegisteredTaskRoot();
		this.taskRegistrations = TaskRepoManager.scanForTasks(taskReposDir);
		this.tasks = new HashMap<>();
		this.uuidGenerator = new UUIDGenerator();
		uuidGenerator.reserveAll(taskRegistrations.keySet());

		for (Map.Entry<String,RegistrationTag> entry : taskRegistrations.entrySet()) {
			String uuid = entry.getKey();
			RegistrationTag r = entry.getValue();
			File dir = new File(checkoutLocation,uuid);
			if (!dir.exists()) {
				cloneTask(r);
			}
			Task t = loadTask(uuid,r.isActive(), new File(dir,"task.json"));
			if (t!=null) {
				tasks.put(t.getTaskId(), t);
			}
		}		
	}
	
	/**
	 * Take a clone of the task repository at the version given and use it as a new task
	 * 
	 * @param registrationTag
	 * @return a task object containing information about the task
	 * @throws InvalidTagFormatException
	 * @throws GitAPIException
	 * @throws IOException
	 */
	public Task cloneTask(RegistrationTag registrationTag) throws InvalidTagFormatException, GitAPIException, IOException {
		
		// TODO: we also need to compile the source code for the test...
		String uuid = registrationTag.getRegisteredTaskUUID();
		File taskDir = new File(checkoutLocation,uuid);
		Task t = null;
		try {
			try (Git g = Git.cloneRepository().setURI(registrationTag.getRepository().getPath()).setDirectory(taskDir).call()) {
				g.reset().setMode(ResetType.HARD).setRef(registrationTag.toTagName()).call();
			}
			t = loadTask(uuid,registrationTag.isActive(),new File(taskDir,"task.json"));
		}
		finally {
			if (t == null)
				FileUtil.deleteRecursive(taskDir);
		}
		if (t != null) {
			tasks.put(t.getTaskId(), t);
			taskRegistrations.put(uuid, registrationTag);
		}
		return t;		
	}
	
	/**
	 * Allocate a new UUID for a task (make sure its unique)
	 * @return the new UUID
	 */
	public String reserveNewTaskUuid() {
		return uuidGenerator.generate();
	}
	
	public RegistrationTag getRegistration(String taskId) {
		return taskRegistrations.get(taskId);
	}
	
	public Map<String,RegistrationTag> getRegistrations() {
		return taskRegistrations;
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
