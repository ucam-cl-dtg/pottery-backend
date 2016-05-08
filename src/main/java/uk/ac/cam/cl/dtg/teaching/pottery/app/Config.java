package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {

	public static final Logger LOG = LoggerFactory.getLogger(Config.class);
	
	private static final File PREFIX= new File("/opt/pottery");
	private static final File TASK_PREFIX = new File(PREFIX,"tasks");
	
	private String userName;

	private int uid;
	
	public Config() {
		this.userName = System.getProperty("user.name");
		this.uid = getUID(userName);
	}
	
	private static int getUID(String userName) {
	    try {
			Process child = Runtime.getRuntime().exec("/usr/bin/id -u "+userName);
			try {
				return Integer.parseInt(IOUtils.readLines(child.getInputStream()).get(0));
			}
			finally {
				child.destroyForcibly();
			}
		} catch (NumberFormatException e) {
			LOG.error("Failed to parse UID",e);
			return 0;
		} catch (IOException e) {
			LOG.error("Failed to run id to find UID");
			return 0;
		}
	}
	
	
	
	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public int getUid() {
		return uid;
	}

	public void setUid(int uid) {
		this.uid = uid;
	}

	public File getRepoRoot() {
		return new File(PREFIX,"repos");
	}
	
	public File getRepoTestingRoot() {
		return new File(PREFIX,"repo-testing");
	}
	
	public File getLibRoot() {
		return new File(PREFIX,"lib");
	}
		
	public String getWebtagPrefix() {
		return "online-";
	}

	public File getTaskDefinitionRoot() {
		return new File(TASK_PREFIX,"def");
	}
	
	public File getTaskTestingRoot() {
		return new File(TASK_PREFIX,"test");
	}
	
	public File getTaskReleaseRoot() {
		return new File(TASK_PREFIX,"release");
	}
	
	public File getTaskRetiredRoot() {
		return new File(TASK_PREFIX,"retired");
	}
	
	public File getTaskTemplateRoot() {
		return new File(TASK_PREFIX,"template");
	}
	
	public File getTaskStagingRoot() {
		return new File(TASK_PREFIX,"staging");
	}
	
	public File getTaskOutgoingRoot() {
		return new File(TASK_PREFIX,"outgoing");
	}
}
