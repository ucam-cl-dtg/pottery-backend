package uk.ac.cam.cl.dtg.teaching.pottery.config;

import java.io.File;

public class TaskConfig {

	private File taskPrefix;

	public TaskConfig() {
		this.taskPrefix = new File(Config.PREFIX,"tasks");
	}
	
	public TaskConfig(File taskPrefix) {
		this.taskPrefix = taskPrefix;
	}

	public File getTaskTemplateRoot() {
		return new File(taskPrefix,"template");
	}
	
	public File getTaskDefinitionRoot() {
		return new File(taskPrefix,"def");
	}

	public File getTaskCopyRoot() {
		return new File(taskPrefix,"copy");
	}

	public File getTaskDefinitionDir(String taskId) {
		return new File(getTaskDefinitionRoot(),taskId);
	}
	
	public File getTaskCopyDir(String copyId) {
		return new File(new File(taskPrefix,"copy"),copyId);
	}

	public File getSolutionDir(String copyId) {
		return new File(getTaskCopyDir(copyId),"solution");
	}

	public File getCompileDir(String copyId) {
		return new File(getTaskCopyDir(copyId),"compile");
	}

	public File getHarnessDir(String copyId) {
		return new File(getTaskCopyDir(copyId),"harness");
	}

	public File getValidatorDir(String copyId) {
		return new File(getTaskCopyDir(copyId),"validator");
	}
	
	public File getSkeletonDir(String copyId) {
		return new File(getTaskCopyDir(copyId),"skeleton");
	}	
}