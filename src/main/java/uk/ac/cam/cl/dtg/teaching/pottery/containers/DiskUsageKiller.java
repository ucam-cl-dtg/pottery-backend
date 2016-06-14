package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerInfo;

public class DiskUsageKiller implements Runnable {

	protected static final Logger LOG = LoggerFactory.getLogger(DiskUsageKiller.class);
	
	private final DockerApi docker;
	private final String containerId;
	private final int maxBytes;
	
	private boolean killed = false;
	private int bytesWritten = 0;
	
	public DiskUsageKiller(String containerId, DockerApi docker, int maxBytes) {
		this.containerId = containerId;
		this.docker = docker;
		this.maxBytes = maxBytes;
	}
	
	@Override
	public void run() {
		try {
			ContainerInfo i = docker.inspectContainer(containerId, true);
			if (i != null && i.getSizeRw() > this.maxBytes) {
				boolean killed = DockerUtil.killContainer(containerId, docker);
				synchronized(this) {
					this.bytesWritten = i.getSizeRw();
					this.killed = killed;
				}
			} 
		} catch (RuntimeException e) {
			LOG.error("Caught exception when trying to kill container for disk usage",e);
		}
	}
	
	public synchronized boolean isKilled() {
		return this.killed;
	}
	
	public synchronized int getBytesWritten() {
		return this.bytesWritten;
	}	
}
