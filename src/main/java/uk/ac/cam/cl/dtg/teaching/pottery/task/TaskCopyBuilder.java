package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.CompilationResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.ValidationResponse;

/**
 * Class for building a taskcopy. Responsible for copying files, compilation
 * etc.
 * 
 * @author acr31
 *
 */
public class TaskCopyBuilder {

	protected final Logger LOG = LoggerFactory.getLogger(TaskCopyBuilder.class);

	/**
	 * DTO with information about the current state of this copy 
	 */
	private final BuilderInfo builderInfo;
	
	/**
	 * The TaskCopy object that this builder is building. Starts off as null and then gets set asynchronously
	 */
	private volatile TaskCopy taskCopy;

	/**
	 * Worker object for copying files into this TaskCopy
	 */
	private final Job copyFiles;
	
	/**
	 * Worker object for compiling the tests in this TaskCopy
	 */
	private final Job compileTests;
	
	/**
	 * Instances of this class should be created by the Task object only. The
	 * task object has to ensure that there is only one instance per copyId
	 * 
	 * @param sha1
	 *            the SHA1 of the parent repo to copy
	 * @param taskId
	 *            the ID of the task we are copying for
	 * @param copyId
	 *            the ID of the copy
	 * @param taskConfig
	 *            config information for tasks
	 */
	TaskCopyBuilder(String sha1, String taskId, String copyId, TaskConfig taskConfig) {
		final File taskDefDir = taskConfig.getTaskDefinitionDir(taskId);				


		// We know that noone else will have access to the TaskCopy until we are done
		// 1) our copyId is unique and Task ensures that there is only one instance of TaskCopyBuilder for each copyId
		// 2) we only give out a reference to the TaskCopy once the BuilderInfo status is set to success. And this only happens after we finish
		// Therefore no locks are needed on this lot
		
		this.builderInfo = new BuilderInfo(sha1);
		this.taskCopy = null;
		
		if (copyId != null && taskConfig.getTaskCopyDir(copyId).exists()) {
			this.taskCopy = new TaskCopy(taskId, copyId, sha1, taskConfig);
			this.builderInfo.setStatus(BuilderInfo.STATUS_SUCCESS);
		}
		
		if (copyId == null) {
			this.builderInfo.setStatus(BuilderInfo.STATUS_SUCCESS);
		}
		
		this.copyFiles = new Job() {
			@Override
			public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
					Database database) throws Exception {
				
				// We are the only object writing to this directory (we have a unique id)
				// As many threads as you like can read from the bare git repo
				// Assignments to builderInfo are atomic
				// => No locks needed
				
				builderInfo.setStatus(BuilderInfo.STATUS_COPYING_FILES);
				LOG.info("Copying files for {} into {}",taskDefDir,copyId);
				File location = taskConfig.getTaskCopyDir(copyId);
				
				// copy the files from the repo
				try (Git g = Git.cloneRepository().setURI(taskDefDir.getPath()).setDirectory(location).call()) {
						g.reset().setMode(ResetType.HARD).setRef(sha1).call();
				} catch (GitAPIException e) {
					builderInfo.setException(new TaskCloneException("Failed to create clone of " + taskDefDir + " and reset to " + sha1,e));
					return false;
				}
				
				try {
					taskCopy = new TaskCopy(taskId,copyId,sha1,taskConfig);
				} catch (IOException e) {
					builderInfo.setException(new TaskCloneException("Failed to load task info",e));
					return false;
				}
				
				return true;
			}
		};
		this.compileTests = new Job() {
			@Override
			public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
					Database database) throws Exception {
				
				String copyId = taskCopy.getCopyId();		

				LOG.info("Compiling tests for {} into {}",taskDefDir,copyId);
				TaskInfo taskInfo = taskCopy.getInfo();
				String image = taskInfo.getImage();

				builderInfo.setStatus(BuilderInfo.STATUS_COMPILING_TEST);
				ExecResponse r = containerManager.execTaskCompilation(taskCopy.getLocation(),image,taskInfo.getCompilationRestrictions());
				if (!r.isSuccess()) {
					builderInfo.setException(new TaskCloneException("Failed to compile testing code in task. "+r.getResponse()));
					return false;
				}
				
				builderInfo.setStatus(BuilderInfo.STATUS_COMPILING_SOLUTION);
				// Test it against the model answer
				CompilationResponse r2 = containerManager.execCompilation(
						taskConfig.getSolutionDir(copyId),
						taskConfig.getCompileDir(copyId), 
						image,
						taskInfo.getCompilationRestrictions());
				if (!r2.isSuccess()) {
					builderInfo.setException(new TaskCloneException("Failed to compile solution when testing task during registration. " + r2.getFailMessage()));
					return false;
				}
				
				builderInfo.setStatus(BuilderInfo.STATUS_TESTING_SOLUTION);
				HarnessResponse r3 = containerManager.execHarness(
						taskConfig.getSolutionDir(copyId),
						taskConfig.getHarnessDir(copyId), 
						image, 
						taskInfo.getHarnessRestrictions());
				if (!r3.isSuccess()) {
					builderInfo.setException(new TaskCloneException("Failed to run harness when testing task during registration. " + r3.getFailMessage()));
					return false;
				}
				
				ValidationResponse r4 = containerManager.execValidator(
						taskConfig.getValidatorDir(copyId), 
						r3, 
						image,
						taskInfo.getValidatorRestrictions());
				if (!r4.isSuccess()) {
					builderInfo.setException(new TaskCloneException("Failed to validate harness results when testing task during registration. " + r4.getFailMessage()));
					return false;
				}
				
				builderInfo.setStatus(BuilderInfo.STATUS_SUCCESS);
				
				return true;
			}
		};
	}
	
	TaskCopy getTaskCopy() {
		// Don't give out TaskCopy objects until we've finished building
		if (builderInfo.getStatus().equals(BuilderInfo.STATUS_SUCCESS)) {
			return taskCopy;
		}
		else {
			return null;
		}
	}
	
	/**
	 * Schedule the copying and compilation for this copy.  
	 */
	void schedule(Worker w, Job continuation) {
		// synchronize here to eliminate the race between checking the status and marking us scheduled
		synchronized (builderInfo) {
			if (isReplacable()) {
				builderInfo.setStatus(BuilderInfo.STATUS_SCHEDULED);
				w.schedule(copyFiles, 
						compileTests,
						continuation);
			}
		}
	}

	BuilderInfo getBuilderInfo() {
		return builderInfo;
	}

	boolean isReplacable() {
		String status = this.builderInfo.getStatus();
		return 
				BuilderInfo.STATUS_NOT_STARTED.equals(status) ||
				BuilderInfo.STATUS_SUCCESS.equals(status) ||
				BuilderInfo.STATUS_FAILURE.equals(status);
	}
	

}
