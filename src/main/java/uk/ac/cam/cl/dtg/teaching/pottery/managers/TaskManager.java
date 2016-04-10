package uk.ac.cam.cl.dtg.teaching.pottery.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerHelper;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.CompilationResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.ValidationResponse;

@Singleton
public class TaskManager {

	public static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);
	
	// TasksDefinitions are stored as bare git repos. The task uuid is the name of the repo.
	// A testing version of each task is checked out (at head revision)
	// If you release a task you specify the sha1 to release and this is checked out to another directory. New releases just update the checked out version
	// If you retire a task then the released version is left but the testing version is removed

	private DockerApi docker;
	
	private Config config;
	
	private Map<String,Task> definedTasks;
	private Map<String,Task> releasedTasks;
	private Map<String,Task> retiredTasks;
 	
	private UUIDGenerator uuidGenerator;
	
	
	@Inject
	public TaskManager(Config config, DockerApi docker) throws GitAPIException, IOException {
		this.config = config;
		this.docker = docker;
		this.uuidGenerator = new UUIDGenerator();
		this.definedTasks = new HashMap<>();
		this.releasedTasks = new HashMap<>();
		this.retiredTasks = new HashMap<>();
		
		for(File f : config.getTaskDefinitionRoot().listFiles()) {
			if (f.getName().startsWith(".")) continue;
			String uuid = f.getName();
			uuidGenerator.reserve(uuid);
			File taskTestDir = new File(config.getTestingRoot(),uuid);
			if (!taskTestDir.exists()) {
				try (Git g = Git.cloneRepository().setURI(f.getPath()).setDirectory(taskTestDir).call()) {}
			}
			Task t = Task.load(taskTestDir,false);
			definedTasks.put(uuid,t);
		}
		
		for(File f : config.getTaskReleaseRoot().listFiles()) {
			if (f.getName().startsWith(".")) continue;
			String uuid = f.getName();
			Task t = Task.load(f,true);
			releasedTasks.put(uuid,t);
		}
		
		for(File f : config.getTaskRetiredRoot().listFiles()) {
			if (f.getName().startsWith(".")) continue;
			String uuid = f.getName();
			Task t = Task.load(f,false);
			retiredTasks.put(uuid,t);
		}
		
		//TODO: add cleanup code for when a register is interrupted
	}
	
	/**
	 * Creates a new bare git repo to hold a task a pre-populates it with skeleton files
	 * @return 
	 * @throws IOException 
	 */
	public Task createNewTask() throws IOException {
		File taskDefRoot = config.getTaskDefinitionRoot();
		String taskId = uuidGenerator.generate();
				
		File taskDefDir = new File(taskDefRoot,taskId);
		taskDefDir.mkdir();
		
		File taskTestingDir = new File(config.getTaskTestingRoot(),taskId);
		
		File templateRepo = new File(config.getTaskTemplateRoot(),"standard");
		
		try (Git g = Git.cloneRepository().setURI(templateRepo.getPath()).setBare(true).setDirectory(taskDefDir).call()) {
			try (Git g2 = Git.cloneRepository().setURI(taskDefDir.getPath()).setDirectory(taskTestingDir).call()) {
				Task t = Task.load(taskTestingDir,false);
				definedTasks.put(t.getTaskId(), t);
				return t;
			}
		} catch (IllegalStateException|GitAPIException e) {
			IOException toThrow = new IOException("Failed to initialise git repository",e);
			try {
				FileUtil.deleteRecursive(taskDefDir);
			} catch (IOException e1) {
				toThrow.addSuppressed(e1);
			}
			try {
				FileUtil.deleteRecursive(taskTestingDir);
			} catch (IOException e1) {
				toThrow.addSuppressed(e1);
			}
			throw toThrow;
		}
	}
	
	/**
	 * Register this particular version of the task for others to use.
	 * 
	 * This works by cloning (if necessary) the task bare repo and then resetting it to the desired sha1. Re-registering a task just changes the version that is checked out.
	 *  
	 *  @param taskId is the uuid of the task to register
	 *  @param sha1 is the version of the commit to register
	 * @throws TaskRegistrationException 
	 */
	public Task registerTask(String taskId, String sha1) throws TaskRegistrationException {
		File taskDefDir = new File(config.getTaskDefinitionRoot(),taskId);
		File taskStagingDir = new File(config.getTaskStagingRoot(),taskId);
		File taskReleaseDir = new File(config.getTaskReleaseRoot(),taskId);
		File taskOutgoingDir = new File(config.getTaskOutgoingRoot(),taskId);
		
		Task t;
		try {
			// clone the registered task root in to the staging directory
			// reset to the sha1
			try {
				try(Git g = Git.cloneRepository().setURI(taskDefDir.getPath()).setDirectory(taskStagingDir).call()) {
					g.reset().setRef(sha1).setMode(ResetType.HARD).call();
				}
			}
			catch (GitAPIException e) {
				throw new TaskRegistrationException("Failed to clone task definition",e);
			}
			
			try {
				t = Task.load(taskStagingDir,true);
			} catch (IOException e) {
				throw new TaskRegistrationException("Failed to load task definition",e);
				
			}
			
			// Compile the testing code
			ExecResponse r = ContainerHelper.execTaskCompilation(taskStagingDir,t.getImage(),docker,config);
			if (!r.isSuccess()) {
				throw new TaskRegistrationException("Failed to compile testing code in task. "+r.getResponse());
			}
			
			// Test it against the model answer
			CompilationResponse r2 = ContainerHelper.execCompilation(new File(taskStagingDir,"solution"), new File(taskStagingDir,"compile"),t.getImage(), docker,config);
			if (!r2.isSuccess()) {
				throw new TaskRegistrationException("Failed to compile solution when testing task for release. "+r2.getFailMessage());
			}
			HarnessResponse r3 = ContainerHelper.execHarness(new File(taskStagingDir,"solution"), new File(taskStagingDir,"harness"), t.getImage(), docker,config);
			if (!r3.isSuccess()) {
				throw new TaskRegistrationException("Failed to run harness when testing task for release. "+r3.getFailMessage());
			}
			ValidationResponse r4 = ContainerHelper.execValidator(new File(taskStagingDir,"validator"), r3, t.getImage(), docker,config);
			if (!r4.isSuccess()) {
				throw new TaskRegistrationException("Failed to validate harness results when testing task for release. "+r4.getFailMessage());
			}
		} catch (TaskRegistrationException e) {
			try {
				FileUtil.deleteRecursive(taskStagingDir);
			}
			catch (IOException e1) {
				e.addSuppressed(e1);
			}
			throw e;
		}

		if (releasedTasks.containsKey(taskId)) {
			//releasedTasks.get(taskId).setLocked(true);
			// TODO: abort all running tests
			taskReleaseDir.renameTo(taskOutgoingDir);
		}
		
		// We shuffle these directories around so that we can recover if the server goes down in the middle
		taskStagingDir.renameTo(taskReleaseDir);

		if (taskOutgoingDir.exists()) {
			try {
				FileUtil.deleteRecursive(taskOutgoingDir);
			} catch (IOException e) {
				LOG.warn("Failed to delete outgoing task directory "+taskOutgoingDir.getPath(),e);
			}
		}
		
		releasedTasks.remove(taskId);
		releasedTasks.put(taskId, t);
		
		return t;
	}
	
	/**
	 * Moves a task to the retired list. People will not be able to start new submissions (or retest existing ones)
	 * 
	 * @param taskId the uuid of the task to retire
	 */
	public void retireTasks(String taskId) {
		
	}
	
	public Collection<Task> getDefinedTasks() {
		return new LinkedList<Task>(definedTasks.values());
	}
	
	public Collection<Task> getReleasedTasks() {
		return new LinkedList<Task>(releasedTasks.values());
	}

	public Collection<Task> getRetiredTasks() {
		return new LinkedList<Task>(retiredTasks.values());
	}

	public Task getTask(String taskId) {
		return definedTasks.get(taskId);
	}

	public Task getReleasedTask(String taskId) {
		return releasedTasks.get(taskId);
	}
		
}
