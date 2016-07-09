package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
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
	
	
	public static final String STATUS_NOT_STARTED = "NOT_STARTED";
	public static final String STATUS_SCHEDULED = "SCHEDULED";
	public static final String STATUS_COPYING_FILES = "COPYING_FILES";
	public static final String STATUS_COMPILING_TEST = "COMPILING_TEST";
	public static final String STATUS_COMPILING_SOLUTION = "COMPILING_SOLUTION";
	public static final String STATUS_TESTING_SOLUTION = "TESTING_SOLUTION";
	public static final String STATUS_SUCCESS = "SUCCESS";
	public static final String STATUS_FAILURE = "FAILURE";

	private TaskCopyBuilderInfo builderInfo;
	
	private TaskCopy taskCopy;

	private Job copyFiles;
	private Job compileTests;
	
	public TaskCopyBuilder(String sha1, String taskId, TaskConfig taskConfig, UUIDGenerator uuidGenerator) {
		this.builderInfo = new TaskCopyBuilderInfo(sha1);
		File taskDefDir = taskConfig.getTaskDefinitionDir(taskId);
		this.copyFiles = new Job() {
			@Override
			public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
					Database database) throws Exception {
				
				builderInfo.setStatus(STATUS_COPYING_FILES);
				String copyId = uuidGenerator.generate();
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

				builderInfo.setStatus(STATUS_COMPILING_TEST);
				ExecResponse r = containerManager.execTaskCompilation(taskCopy.getLocation(),image,taskInfo.getCompilationRestrictions());
				if (!r.isSuccess()) {
					builderInfo.setException(new TaskCloneException("Failed to compile testing code in task. "+r.getResponse()));
					return false;
				}
				
				builderInfo.setStatus(STATUS_COMPILING_SOLUTION);
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
				
				builderInfo.setStatus(STATUS_TESTING_SOLUTION);
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
				
				builderInfo.setStatus(STATUS_SUCCESS);
				
				return true;
			}
		};
	}

	/**
	 * Initialiser for loading a builder which has already completed
	 */
	public TaskCopyBuilder(String sha1, String taskId, TaskConfig taskConfig, UUIDGenerator uuidGenerator, String copyId) throws IOException {
		this.builderInfo = new TaskCopyBuilderInfo(sha1);
		this.builderInfo.setStatus(STATUS_SUCCESS);
		this.taskCopy = new TaskCopy(taskId,copyId,sha1,taskConfig);
		File taskDefDir = taskConfig.getTaskDefinitionDir(taskId);
		// Its invalid to try building this so just return false
		this.copyFiles = new Job() {
			@Override
			public boolean execute(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager,
					Database database) throws Exception {
				LOG.warn("Ignored execution stage for {} copy={}",taskDefDir,copyId);
				return false;
			}			
		};
		this.compileTests = copyFiles;

		
	}
	
	public TaskCopy getTaskCopy() {
		return taskCopy; 
	}
	
	/**
	 * Schedule the copying and compilation for this copy. 
	 */
	public void schedule(Worker w, Job continuation) {
		builderInfo.setStatus(STATUS_SCHEDULED);
		w.schedule(copyFiles, 
				compileTests,
				continuation);
	}

	public TaskCopyBuilderInfo getBuilderInfo() {
		return builderInfo;
	}

	

}
