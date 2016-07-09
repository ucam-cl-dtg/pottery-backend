package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

/**
 * The definition of a task is a git repository containing info about the task,
 * skeleton files, harness and validator. This is stored in [task_root]/def.
 * 
 * Tasks have a testing version which is tracks HEAD of the task definition
 * repository. 
 * 
 * Tasks can also have a registered version which corresponds to a chosen tag in
 * the definition repository. 
 * 
 * The testing and registered versions are stored as copies of the relevant files from the git repo in [task_root]/copy/*
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

	/**
	 * The UUID for the task. This is constant and doesn't change throughout the lifecycle of the task.
	 */
	private final String taskId;

	private boolean retired;

	/**
	 * The taskdef is a bare git repo which is the upsteam source for the task
	 */
	private final File taskDefDir;

	private final TaskConfig config;
	
	/**
	 * Used to generate unique taskIds and unique copyIds
	 */
	private final UUIDGenerator uuidGenerator;

	/**
	 * Private constructor. TaskFactory creates instances of this class calling static constructor methods.
	 */
	private Task(String taskId, TaskCopyBuilder testingBuilder, TaskCopy testingCopy, TaskCopyBuilder registeredBuilder,
			TaskCopy registeredCopy, boolean retired, TaskConfig config, UUIDGenerator uuidGenerator) {
		super();
		this.taskId = taskId;
		this.testingBuilder = testingBuilder;
		this.testingCopy = testingCopy;
		this.registeredBuilder = registeredBuilder;
		this.registeredCopy = registeredCopy;
		this.retired = retired;
		this.taskDefDir = config.getTaskDefinitionDir(taskId);
		this.config = config;
		this.uuidGenerator = uuidGenerator;		
	}

	public String getTaskId() {
		return taskId;
	}

	public boolean isRetired() {
		return retired;
	}

	
	//*** BEGIN METHODS FOR MANAGING REGISTRATION OF THE TASK AND THE REGISTERED COPY ***
	
	/**
	 * Object for building a new registered version of this task and
	 * representing the progress through that job. Should never be null
	 */
	private volatile TaskCopyBuilder registeredBuilder;

	/**
	 * Object representing the registered version of this task. If its null there is no registered version.
	 */
	private volatile TaskCopy registeredCopy;
	
	/**
	 * Mutex for managing access to the registration fields (registeredbuilder and registeredcopy)
	 */
	private final Object registeredMutex = new Object();
	
	public TaskCopy getRegisteredCopy() {
		// no synchronization needed since registeredCopy is volatile and assignments are atomic
		return registeredCopy;
	}

	public BuilderInfo getRegisteredCopyBuilderInfo() {
		// no synchronization needed since registeredbuilder is volatile and assignments are atomic
		return registeredBuilder.getBuilderInfo(); 
	}

	public BuilderInfo scheduleBuildRegisteredCopy(String sha1, Worker w) throws TaskCloneException {
		if ("HEAD".equals(sha1)) {
			sha1 = getHeadSHA();
		}

		// We need to ensure there is only one thread in this region at a time
		// or else we have a race between deciding that we should start a new copy and updating 
		// registeredBuilder to record it
		synchronized (registeredMutex) {
			if (!registeredBuilder.isReplacable()) {
				return registeredBuilder.getBuilderInfo();
			}
			registeredBuilder = new TaskCopyBuilder(sha1,taskId,uuidGenerator.generate(),config);

			registeredBuilder.schedule(w, new Job() {
				@Override
				public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
						Database database) throws Exception {
					return storeNewRegisteredCopy(w, database);
				}
			});
			return registeredBuilder.getBuilderInfo();
		}
	}

	private boolean storeNewRegisteredCopy(Worker w, Database database) {
		// We need to ensure that only one thread is in this region at a time for any particular task instance
		// or else we have a race on reading the old value of registeredCopy and replacing it with the new one
		synchronized(registeredMutex) {
			TaskCopy oldCopy = registeredCopy;
			try (TransactionQueryRunner q = database.getQueryRunner()) {
				TaskDefInfo.updateRegisteredCopy(taskId, registeredBuilder.getBuilderInfo().getSha1(), registeredBuilder.getTaskCopy().getCopyId(), q);
				q.commit();
				registeredCopy = registeredBuilder.getTaskCopy();
				if (oldCopy != null) {
					w.schedule(new Job() {
						@Override
						public boolean execute(TaskManager taskManager, RepoFactory repoFactory,
								ContainerManager containerManager, Database database) throws Exception {
							oldCopy.destroy();
							return true;
						}						
					});
				}
			}
			catch (SQLException e) {
				registeredBuilder.getBuilderInfo().setException(new TaskCloneException("Failed to record changes in database",e));
				w.schedule(new Job() {
					@Override
					public boolean execute(TaskManager taskManager, RepoFactory repoFactory,
							ContainerManager containerManager, Database database) throws Exception {
						registeredBuilder.getTaskCopy().destroy();
						return true;
					}
				});
				return false;
			}
			return true;
		}
	}
	
	//*** END METHODS FOR MANAGING THE REGISTERED COPY OF THE TASK ***
	
	//*** BEGIN METHODS FOR MANAGING THE TESTING COPY OF THE TASK ***
	
	/**
	 * Object for building a new testing version of the task and representing
	 * the progress through that job. Should never be null. Access to this object should be protected using the testingMutex object.
	 */
	private volatile TaskCopyBuilder testingBuilder;

	/**
	 * Object representing the testing version of the task. If its null there is
	 * no testing version (or one is in progress of being built).
	 */
	private volatile TaskCopy testingCopy;

	/**
	 * This mutex is used to protect access to testingBuilder and testingCopy
	 */
	private final Object testingMutex = new Object();
	
	public TaskCopy getTestingCopy() { 
		// No mutex needed since testingCopy is volatile and updates to it are atomic
		return testingCopy; 
	}
	
	public BuilderInfo getTestingCopyBuilderInfo() {
		// No mutex needed since testingBuilder is volatile and updates to it are atomic
		return testingBuilder.getBuilderInfo(); 
	}
	
	public BuilderInfo scheduleBuildTestingCopy(Worker w) throws TaskCloneException {
		// We need to ensure there is only one thread in this region at a time
		// or else we have a race between deciding that we should start a new copy and updating 
		// testingBuilder to record it
		synchronized (testingMutex) {
			if (!testingBuilder.isReplacable()) {
				return testingBuilder.getBuilderInfo();
			}
				
			LOG.info("Scheduling testing build for "+taskDefDir);

			testingBuilder = new TaskCopyBuilder(getHeadSHA(),taskId,uuidGenerator.generate(),config);
			
			testingBuilder.schedule(w, new Job() {
				@Override
				public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
						Database database) throws Exception {
					return storeNewTestCopy(w, database);
				}
			});
			
			return testingBuilder.getBuilderInfo();
		}
	}
	
	private boolean storeNewTestCopy(Worker w, Database database) {
		// We need to ensure that only one thread is in this region at a time for any particular task instance
		// or else we have a race on reading the old value of testingCopy and replacing it with the new one
		synchronized (testingMutex) {
			TaskCopy oldCopy = testingCopy;
			try (TransactionQueryRunner q = database.getQueryRunner()) {
				TaskDefInfo.updateTestingCopy(taskId, testingBuilder.getTaskCopy().getCopyId(), q);
				q.commit();
				testingCopy = testingBuilder.getTaskCopy();
				if (oldCopy != null) {
					w.schedule(new Job() {
						@Override
						public boolean execute(TaskManager taskManager, RepoFactory repoFactory,
								ContainerManager containerManager, Database database) throws Exception {
							oldCopy.destroy();
							return true;
						}
					});
				}
				return true;
			}
			catch (SQLException e) {
				testingBuilder.getBuilderInfo().setException(new TaskCloneException("Failed to record changes in database",e));
				w.schedule(new Job() {
					@Override
					public boolean execute(TaskManager taskManager, RepoFactory repoFactory,
							ContainerManager containerManager, Database database) throws Exception {
						testingBuilder.getTaskCopy().destroy();
						return true;
					}
				});
				return false;
			}
		}
	}

	//*** END METHODS FOR MANAGING TEST COPY ***
	
		
	/**
	 * Lookup the SHA1 for the HEAD of the repo
	 * 
	 * @return a string containing the SHA1
	 * @throws TaskCloneException
	 *             if an error occurs trying to read the repo
	 */
	public String getHeadSHA() throws TaskCloneException {
		try (Git g = Git.open(taskDefDir)) {
			Ref r = g.getRepository().findRef(Constants.HEAD);
			return r.getObjectId().getName();
		} catch (IOException e) {
			throw new TaskCloneException("Failed to read Git repository for " + taskDefDir, e);
		}
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
	static Task openTask(String taskId, UUIDGenerator uuidGenerator, Database database, TaskConfig config) throws TaskException, IOException {
		
		try (TransactionQueryRunner q = database.getQueryRunner()) {
			TaskDefInfo info = TaskDefInfo.getByTaskId(taskId, q);
			if (info != null) {				
				TaskCopyBuilder testingBuilder;
				if (info.getTestingCopyId() == null) {
					// No test version has been built before
					// Use null for the copyId 
					testingBuilder = new TaskCopyBuilder("HEAD", taskId, null, config);
				}
				else {
					// The test version has been built before
					testingBuilder = new TaskCopyBuilder("HEAD", taskId, info.getTestingCopyId(),config);
				}
				
				// Mark the status as SUCCESS so we don't try to compile it
				testingBuilder.getBuilderInfo().setStatus(BuilderInfo.STATUS_SUCCESS);
				
				TaskCopyBuilder registeredBuilder;
				if (info.getRegisteredCopyId() == null) {
					registeredBuilder = new TaskCopyBuilder("HEAD",taskId,null, config);
				}
				else {
					registeredBuilder = new TaskCopyBuilder(info.getRegisteredTag(),taskId, info.getRegisteredCopyId(),config);
				}
				
				// Mark the status as SUCCESS so we don't try to compile it
				registeredBuilder.getBuilderInfo().setStatus(BuilderInfo.STATUS_SUCCESS);
				
				return new Task(info.getTaskId(),
						testingBuilder,testingBuilder.getTaskCopy(),
						registeredBuilder,registeredBuilder.getTaskCopy(),
						info.isRetired(),config, uuidGenerator);
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
	static Task createTask(String taskId, UUIDGenerator uuidGenerator, TaskConfig config, Database database) throws TaskException, IOException {

		// create the task directory and clone the template
		File taskDefDir = config.getTaskDefinitionDir(taskId);
		
		taskDefDir.mkdir();
			
		TaskDefInfo info = new TaskDefInfo(taskId, null,null,null,false);

		File templateRepo = new File(config.getTaskTemplateRoot(), "standard");

		try (Git g = Git.cloneRepository().setURI(templateRepo.getPath()).setBare(true).setDirectory(taskDefDir)
				.call()) {
			
			TaskCopyBuilder testingBuilder = new TaskCopyBuilder("HEAD",taskId,null,config);
			TaskCopyBuilder registeredBuilder = new TaskCopyBuilder("HEAD",taskId,null,config);

			// Mark status as success so that we don't try to compile these
			testingBuilder.getBuilderInfo().setStatus(BuilderInfo.STATUS_SUCCESS);
			registeredBuilder.getBuilderInfo().setStatus(BuilderInfo.STATUS_SUCCESS);
			
			try (TransactionQueryRunner q = database.getQueryRunner()) {
				info.insert(q);
				q.commit();
				return new Task(taskId,testingBuilder,null,registeredBuilder,null,false,config, uuidGenerator);
			}
		} catch (GitAPIException | SQLException e) {
			TaskException toThrow = new TaskException("Failed to initialise task " + taskId, e);
			try {
				FileUtil.deleteRecursive(taskDefDir);
			} catch (IOException e1) {
				toThrow.addSuppressed(e1);
			}
			throw toThrow;
		}
	}
}
