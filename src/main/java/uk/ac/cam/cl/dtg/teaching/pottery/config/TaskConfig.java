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
	
	public File getTaskTestingRoot() {
		return new File(taskPrefix,"test");
	}
	
	public File getTaskRegisteredRoot() {
		return new File(taskPrefix,"registered");
	}
		
	public File getTaskTemplateRoot() {
		return new File(taskPrefix,"template");
	}
	
	public File getTaskStagingRoot() {
		return new File(taskPrefix,"staging");
	}
	
	public File getTaskOutgoingRoot() {
		return new File(taskPrefix,"outgoing");
	}
	
}