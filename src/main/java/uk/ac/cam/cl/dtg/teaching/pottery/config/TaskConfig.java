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
	
	public File getTaskDefinitionRoot() {
		return new File(taskPrefix,"def");
	}
	
	public File getTaskDefinitionRoot(String taskId) {
		return new File(getTaskDefinitionRoot(),taskId);
	}


	public File getTaskTemplateRoot() {
		return new File(taskPrefix,"template");
	}
	
	public File getTaskCopyRoot(String copyId) {
		return new File(new File(taskPrefix,"copy"),copyId);
	}

	public File getSolutionRoot(String copyId) {
		return new File(getTaskCopyRoot(copyId),"solution");
	}

	public File getCompileRoot(String copyId) {
		return new File(getTaskCopyRoot(copyId),"compile");
	}

	public File getHarnessRoot(String copyId) {
		return new File(getTaskCopyRoot(copyId),"harness");
	}

	public File getValidatorRoot(String copyId) {
		return new File(getTaskCopyRoot(copyId),"validator");
	}
	
	public File getSkeletonRoot(String copyId) {
		return new File(getTaskCopyRoot(copyId),"skeleton");
	}

	public File getTaskCopyRoot() {
		return new File(taskPrefix,"copy");
	}
	
	
	
}