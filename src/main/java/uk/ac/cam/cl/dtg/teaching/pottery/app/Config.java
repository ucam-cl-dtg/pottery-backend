package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.io.File;

public class Config {

	private static final File PREFIX= new File("/opt/pottery");
	
	public File getRepoRoot() {
		return new File(PREFIX,"repos");
	}
	
	public File getTestingRoot() {
		return new File(PREFIX,"working-test");
	}
	
	public File getLibRoot() {
		return new File(PREFIX,"lib");
	}
	
	public String getHeadTag() {
		return "HEAD";
	}
	
	
	public String getWebtagPrefix() {
		return "online-";
	}

	public File getRegisteredTaskRoot() {
		return new File(PREFIX,"working-tasks");
	}
	
	public File getTaskRepoRoot() {
		return new File(PREFIX,"tasks-bare");
	}
	
}
