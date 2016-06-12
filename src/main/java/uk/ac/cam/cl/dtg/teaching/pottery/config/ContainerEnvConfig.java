package uk.ac.cam.cl.dtg.teaching.pottery.config;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerEnvConfig {

	public static final Logger LOG = LoggerFactory.getLogger(ContainerEnvConfig.class);
		
	private String userName;

	private int uid;

	private File libDir;
	
	public ContainerEnvConfig(File libDir) {
		this();
		this.libDir = libDir;
	}
	
	public ContainerEnvConfig() {
		this.userName = System.getProperty("user.name");
		this.uid = getUID(userName);
		this.libDir = new File(Config.PREFIX,"lib");
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

	public int getUid() {
		return uid;
	}
	
	public File getLibRoot() {
		return libDir;
	}
	
	/**
	 * Returns the maximum number of bytes a container is allowed to write before it is killed
	 * 
	 * @return maximum size in bytes
	 */
	public int getDiskWriteLimitBytes() {
		return 20*1024*1024;
	}
}
