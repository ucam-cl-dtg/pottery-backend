package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ContainerRestrictions {

	private int timeoutSec;
	
	private int diskWriteLimitMegabytes;
	
	private int ramLimitMegabytes;
	
	public ContainerRestrictions() {
		super();
		this.timeoutSec = -1;
		this.diskWriteLimitMegabytes = 0;
		this.ramLimitMegabytes = 10;
	}
	
	@JsonCreator
	public ContainerRestrictions(
			@JsonProperty("timeoutSec") int timeoutSec, 
			@JsonProperty("diskWriteLimitMegabytes") int diskWriteLimitMegabytes,
			@JsonProperty("ramLimitMegabytes") int ramLimitMegabytes) {
		super();
		this.timeoutSec = timeoutSec;
		this.diskWriteLimitMegabytes = diskWriteLimitMegabytes;
		this.ramLimitMegabytes = ramLimitMegabytes;
	}

	public int getTimeoutSec() {
		return timeoutSec;
	}

	public int getDiskWriteLimitMegabytes() {
		return diskWriteLimitMegabytes;
	}

	public int getRamLimitMegabytes() {
		return ramLimitMegabytes;
	}
	
}
