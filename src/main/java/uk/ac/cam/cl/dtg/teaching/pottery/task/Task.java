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

	private String taskId;

	/**
	 * Object for building a new testing version of the task and representing
	 * the progress through that job. Should never be null
	 */
	private TaskCopyBuilder testingBuilder;

	/**
	 * Object representing the testing version of the task. If its null there is
	 * no testing version (or one is in progress of being built)
	 */
	private TaskCopy testingCopy;

	/**
	 * Object for building a new registered version of this task and
	 * representing the progress through that job. Should never be null
	 */
	private TaskCopyBuilder registeredBuilder;

	/**
	 * Object representing the registered version of this task. If its null there is no registered verion.
	 */
	private TaskCopy registeredCopy;
	
	private String registeredTag;

	private boolean retired;

	/**
	 * The taskdef is a bare git repo which is the upsteam source for the task
	 */
	private final File taskDefDir;

	private final TaskConfig config;
	
	private UUIDGenerator uuidGenerator;

	private Task(String taskId, TaskCopyBuilder testingBuilder, TaskCopy testingCopy, TaskCopyBuilder registeredBuilder,
			TaskCopy registeredCopy, String registeredTag, boolean retired, TaskConfig config, UUIDGenerator uuidGenerator) {
		super();
		this.taskId = taskId;
		this.testingBuilder = testingBuilder;
		this.testingCopy = testingCopy;
		this.registeredBuilder = registeredBuilder;
		this.registeredCopy = registeredCopy;
		this.registeredTag = registeredTag;
		this.retired = retired;
		this.taskDefDir = config.getTaskDefinitionDir(taskId);
		this.config = config;
		this.uuidGenerator = uuidGenerator;		
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

	public TaskCopy getTestingCopy() {
		return testingCopy;
	}
	
	public TaskCopy getRegisteredCopy() {
		return registeredCopy;
	}
	
	public TaskCopyBuilderInfo getTestingCopyBuilderInfo() { return testingBuilder.getBuilderInfo(); }
	
	public TaskCopyBuilderInfo getRegisteredCopyBuilderInfo() { return registeredBuilder.getBuilderInfo(); }
	
	public TaskCopyBuilderInfo scheduleBuildRegisteredCopy(String sha1, Worker w) throws TaskCloneException {
		if (!registeredBuilder.getBuilderInfo().isRunnable()) {
			return registeredBuilder.getBuilderInfo();
		}
		
		if ("HEAD".equals(sha1)) {
			sha1 = getHeadSHA();
		}
		
		registeredBuilder = new TaskCopyBuilder(sha1,taskId,config,uuidGenerator);
		
		registeredBuilder.schedule(w, new Job() {
			@Override
			public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
					Database database) throws Exception {
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
		});
		return registeredBuilder.getBuilderInfo();
	}
	
	public TaskCopyBuilderInfo scheduleBuildTestingCopy(Worker w) throws TaskCloneException {

		if (!testingBuilder.getBuilderInfo().isRunnable()) {
			return testingBuilder.getBuilderInfo();
		}
				
		LOG.info("Scheduling testing build for "+taskDefDir);


		testingBuilder = new TaskCopyBuilder(getHeadSHA(),taskId,config, uuidGenerator);
		
		testingBuilder.schedule(w, new Job() {
			@Override
			public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
					Database database) throws Exception {
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
		});
		
		return testingBuilder.getBuilderInfo();
	}
	
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
					testingBuilder = new TaskCopyBuilder("HEAD", taskId, config, uuidGenerator);
				}
				else {
					testingBuilder = new TaskCopyBuilder("HEAD", taskId, config, uuidGenerator, info.getTestingCopyId());
				}
				
				TaskCopyBuilder registeredBuilder;
				if (info.getRegisteredCopyId() == null) {
					registeredBuilder = new TaskCopyBuilder("HEAD",taskId,config, uuidGenerator);
				}
				else {
					registeredBuilder = new TaskCopyBuilder(info.getRegisteredTag(),taskId,config, uuidGenerator, info.getRegisteredCopyId());
				}
				return new Task(info.getTaskId(),
						testingBuilder,testingBuilder.getTaskCopy(),
						registeredBuilder,registeredBuilder.getTaskCopy(),
						info.getRegisteredTag(),info.isRetired(),config, uuidGenerator);
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
			
			TaskCopyBuilder testingBuilder = new TaskCopyBuilder("HEAD",taskId,config,uuidGenerator);
			TaskCopyBuilder registeredBuilder = new TaskCopyBuilder("HEAD",taskId,config,uuidGenerator);
			
			try (TransactionQueryRunner q = database.getQueryRunner()) {
				info.insert(q);
				q.commit();
				return new Task(taskId,testingBuilder,null,registeredBuilder,null,null,false,config, uuidGenerator);
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
