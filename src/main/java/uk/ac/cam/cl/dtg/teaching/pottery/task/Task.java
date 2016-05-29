package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerHelper;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskRegistrationException;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.CompilationResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.ValidationResponse;

/**
 * The definition of a task is a git repository containing info about the task,
 * skeleton files, harness and validator. This is stored in [task_root]/def.
 * 
 * Tasks have a testing version which is always HEAD of the task definition
 * repository. This is stored in [task_root]/test
 * 
 * Tasks can also have a registered version which corresponds to a chosen tag in
 * the definition repository. This is stored in [task_root]/release
 * 
 * A task can only be deleted if there are no repositories which are attempts at
 * the task
 * 
 * A task can be retired which means that it is no longer available for new
 * repositories. 
 * 
 * @author acr31
 *
 */
public class Task {

	public static final Logger LOG = LoggerFactory.getLogger(Task.class);
	
	private String taskId;
	
	private TaskClone testingClone;
	
	private TaskClone registeredClone;

	private String registeredTag;

	private boolean retired;
	
	private File taskDefRoot;

	private File taskTestingRoot;

	private File taskRegisteredRoot;

	private File taskStagingRoot;

	private Config config;
	
	private Task(String taskId, String registeredTag, boolean retired, Config config) throws TaskException {
		this.taskId = taskId;
		this.registeredTag = registeredTag;
		this.retired = retired;
		this.taskDefRoot = new File(config.getTaskDefinitionRoot(),taskId);
		this.taskTestingRoot = new File(config.getTaskTestingRoot(),taskId);
		this.taskRegisteredRoot = new File(config.getTaskReleaseRoot(),taskId);
		this.taskStagingRoot = new File(config.getTaskStagingRoot(),taskId);
		this.config = config;
		try {
			this.testingClone = new TaskClone(taskDefRoot,taskTestingRoot,Constants.HEAD);
		} catch (TaskCloneException e) {
			throw new TaskException("Failed to open task "+ taskId,e);
		}
		if (registeredTag != null) {
			try {
				this.registeredClone = new TaskClone(taskDefRoot,taskRegisteredRoot,registeredTag);
			}
			catch (TaskCloneException e) {
				LOG.warn("Failed to open registered clone for task. Defaulting back to no registration");
			}
		}
	}
	
	public String getRegisteredTag() { return registeredTag; }
	
	public String getTaskId() { return taskId; }
	
	public boolean isRetired() { return retired; }
	
	public TaskClone getTestingClone() { return testingClone; }
	
	public TaskClone getRegisteredClone() { return registeredClone; }
	
	public synchronized void registerTask(String sha1,DockerApi docker) throws TaskException, TaskCloneException, IOException, TaskRegistrationException {
		
		FileUtil.deleteRecursive(taskStagingRoot);
		TaskClone newClone = new TaskClone(taskDefRoot,taskStagingRoot,sha1);
		
		String image = newClone.getInfo().getImage();
		// Compile the testing code
		ExecResponse r = ContainerHelper.execTaskCompilation(taskStagingRoot,image,docker,config);
		if (!r.isSuccess()) {
			throw new TaskRegistrationException("Failed to compile testing code in task. "+r.getResponse());
		}
		// Test it against the model answer
		CompilationResponse r2 = ContainerHelper.execCompilation(new File(taskStagingRoot,"solution"), new File(taskStagingRoot,"compile"),image, docker,config);
		if (!r2.isSuccess()) {
			throw new TaskRegistrationException("Failed to compile solution when testing task for release. "+r2.getFailMessage());
		}
		HarnessResponse r3 = ContainerHelper.execHarness(new File(taskStagingRoot,"solution"), new File(taskStagingRoot,"harness"), image, docker,config);
		if (!r3.isSuccess()) {
			throw new TaskRegistrationException("Failed to run harness when testing task for release. "+r3.getFailMessage());
		}
		ValidationResponse r4 = ContainerHelper.execValidator(new File(taskStagingRoot,"validator"), r3, image, docker,config);
		if (!r4.isSuccess()) {
			throw new TaskRegistrationException("Failed to validate harness results when testing task for release. "+r4.getFailMessage());
		}
		
		
		newClone.move(taskRegisteredRoot);		
		this.registeredClone = newClone;
		registeredTag = sha1;
	}

	/**
	 * Open an existing task and return the Task object. Don't call this method directly. Instead use TaskFactory.
	 * 
	 * @param taskId
	 * @param config
	 * @return
	 * @throws SQLException 
	 */
	static Task openTask(String taskId, Database database, Config config) throws TaskException {
		try(TransactionQueryRunner q = database.getQueryRunner()) {
			TaskDefInfo info = TaskDefInfo.getByTaskId(taskId, q);
			if (info != null) {
				return new Task(info.getTaskId(),info.getRegisteredTag(),info.isRetired(), config);
			}
			else {
				throw new TaskException("Task "+taskId+" not found");
			}
		} catch (SQLException e) {
			throw new TaskException("Failed to open task "+taskId,e);
		}
	}

	/**
	 * Create a new task from the standard template. Don't call this method directly. Instead use TaskFactory.
	 * @param config
	 * @return
	 * @throws IOException 
	 */
	static Task createTask(String taskId, Config config, Database database) throws TaskException {
		File taskDefRoot = new File(config.getTaskDefinitionRoot(),taskId);
		taskDefRoot.mkdir();

		TaskDefInfo info = new TaskDefInfo(taskId,null,false);
		
		File templateRepo = new File(config.getTaskTemplateRoot(),"standard");
		
		try (Git g = Git.cloneRepository().setURI(templateRepo.getPath()).setBare(true).setDirectory(taskDefRoot).call()) {
			try(TransactionQueryRunner q = database.getQueryRunner()) {
				info.insert(q);
				q.commit();
				return new Task(taskId,null,false, config);
			}
		} catch (GitAPIException|SQLException e) {
			TaskException toThrow = new TaskException("Failed to initialise task "+taskId,e);
			try {
				FileUtil.deleteRecursive(taskDefRoot);
			} catch (IOException e1) {
				toThrow.addSuppressed(e1);
			}
			throw toThrow;
		}
	}
}