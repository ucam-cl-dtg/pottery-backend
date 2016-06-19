package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskRegistrationException;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;
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
 * the definition repository. This is stored in [task_root]/registered
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

	protected static final Logger LOG = LoggerFactory.getLogger(Task.class);

	private String taskId;

	private volatile TaskClone testingClone;

	private volatile TaskClone registeredClone;

	private String registeredTag;

	private boolean retired;

	private final File taskDefRoot;

	private final File taskTestingRoot;

	private final File taskRegisteredRoot;

	private final File taskStagingRoot;

	/**
	 * If not-null this either represents that a version of task is presently
	 * queued for registration or it tells you the result of the previous
	 * registration attempt. You can only have one registration request in the
	 * queue at a time.
	 */
	private volatile TaskCompilation registrationRequest;

	private Task(String taskId, String registeredTag, boolean retired, TaskConfig config) throws TaskException, IOException {
		this.taskId = taskId;
		this.registeredTag = registeredTag;
		this.retired = retired;
		this.taskDefRoot = new File(config.getTaskDefinitionRoot(), taskId);
		this.taskTestingRoot = new File(config.getTaskTestingRoot(), taskId);
		this.taskRegisteredRoot = new File(config.getTaskRegisteredRoot(), taskId);
		this.taskStagingRoot = new File(config.getTaskStagingRoot(), taskId);
		try {
			this.testingClone = new TaskClone(taskDefRoot, taskTestingRoot, Constants.HEAD);
		} catch (TaskCloneException e) {
			throw new TaskException("Failed to open task " + taskId, e);
		}
		if (registeredTag != null) {
			try {
				this.registeredClone = new TaskClone(taskDefRoot, taskRegisteredRoot, registeredTag);
			} catch (TaskCloneException e) {
				LOG.warn("Failed to open registered clone for task. Defaulting back to no registration");
			}
		}
	}
	
	public TaskCompilation getRegistrationRequest() {
		return registrationRequest;
	}

	public String getRegisteredTag() {
		return registeredTag;
	}

	public String getTaskId() {
		return taskId;
	}

	public boolean isRetired() {
		return retired;
	}

	public TaskClone getTestingClone() {
		return testingClone;
	}

	public TaskClone getRegisteredClone() {
		return registeredClone;
	}

	public synchronized TaskCompilation scheduleRegistration(String sha1, Worker w)
			throws TaskException, TaskCloneException, IOException, TaskRegistrationException, SQLException {

		if (registrationRequest != null && registrationRequest.getStatus().equals("PENDING")) {
			return registrationRequest;
		}
		registrationRequest = new TaskCompilation(sha1,"PENDING",null);
		
		// TODO: move registrationRequest and this worker into TaskClone where it can
		// be protected by the right mutex
		
		w.schedule(new Job() {
			@Override
			public void execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
					Database database) throws Exception {
				synchronized (Task.this) {
					FileUtil.deleteRecursive(taskStagingRoot);
					TaskClone newClone = new TaskClone(taskDefRoot, taskStagingRoot, sha1);
					TaskInfo newTaskInfo = newClone.getInfo();
					String image = newTaskInfo.getImage();
					// Compile the testing code
					ExecResponse r = containerManager.execTaskCompilation(
							taskStagingRoot, 
							image, 
							newTaskInfo.getCompilationRestrictions());
					if (!r.isSuccess()) {
						registrationRequest = new TaskCompilation(sha1, "FAILED","Failed to compile testing code in task. "+r.getResponse());
						return;
					}
					// Test it against the model answer
					CompilationResponse r2 = containerManager.execCompilation(
							new File(taskStagingRoot, "solution"),
							new File(taskStagingRoot, "compile"), 
							image,
							newTaskInfo.getCompilationRestrictions());
					if (!r2.isSuccess()) {
						registrationRequest = new TaskCompilation(sha1,"FAILED","Failed to compile solution when testing task during registration. " + r2.getFailMessage());
						return;
					}
					HarnessResponse r3 = containerManager.execHarness(
							new File(taskStagingRoot, "solution"),
							new File(taskStagingRoot, "harness"), 
							image, 
							newTaskInfo.getHarnessRestrictions());
					if (!r3.isSuccess()) {
						registrationRequest = new TaskCompilation(sha1,"FAILED","Failed to run harness when testing task during registration. " + r3.getFailMessage());
						return;
					}
					ValidationResponse r4 = containerManager.execValidator(
							new File(taskStagingRoot, "validator"), 
							r3, 
							image,
							newTaskInfo.getValidatorRestrictions());
					if (!r4.isSuccess()) {
						registrationRequest = new TaskCompilation(sha1,"FAILED","Failed to validate harness results when testing task during registration. " + r4.getFailMessage());
						return;
					}
	
					newClone.move(taskRegisteredRoot);
					Task.this.registeredClone = newClone;
					registeredTag = newClone.getHeadSHA();
					Task.this.registrationRequest = new TaskCompilation(registeredTag,"COMPLETE",null);	
					
					try (TransactionQueryRunner t = database.getQueryRunner()) {
						TaskDefInfo.updateRegisteredTag(taskId, registeredTag, t);
						t.commit();
					}
				}
			}});

			return registrationRequest;
	}

	/**
	 * Open an existing task and return the Task object. Don't call this method
	 * directly. Instead use TaskFactory.
	 * 
	 * @param taskId
	 * @param config
	 * @return
	 * @throws IOException 
	 * @throws SQLException
	 */
	static Task openTask(String taskId, Database database, TaskConfig config) throws TaskException, IOException {
		try (TransactionQueryRunner q = database.getQueryRunner()) {
			TaskDefInfo info = TaskDefInfo.getByTaskId(taskId, q);
			if (info != null) {
				return new Task(info.getTaskId(), info.getRegisteredTag(), info.isRetired(), config);
			} else {
				throw new TaskException("Task " + taskId + " not found");
			}
		} catch (SQLException e) {
			throw new TaskException("Failed to open task " + taskId, e);
		}
	}

	/**
	 * Create a new task from the standard template. Don't call this method
	 * directly. Instead use TaskFactory.
	 * 
	 * @param config
	 * @return
	 * @throws IOException
	 */
	static Task createTask(String taskId, TaskConfig config, Database database) throws TaskException, IOException {
		File taskDefRoot = new File(config.getTaskDefinitionRoot(), taskId);
		taskDefRoot.mkdir();

		TaskDefInfo info = new TaskDefInfo(taskId, null, false);

		File templateRepo = new File(config.getTaskTemplateRoot(), "standard");

		try (Git g = Git.cloneRepository().setURI(templateRepo.getPath()).setBare(true).setDirectory(taskDefRoot)
				.call()) {
			try (TransactionQueryRunner q = database.getQueryRunner()) {
				info.insert(q);
				q.commit();
				return new Task(taskId, null, false, config);
			}
		} catch (GitAPIException | SQLException e) {
			TaskException toThrow = new TaskException("Failed to initialise task " + taskId, e);
			try {
				FileUtil.deleteRecursive(taskDefRoot);
			} catch (IOException e1) {
				toThrow.addSuppressed(e1);
			}
			throw toThrow;
		}
	}
}
