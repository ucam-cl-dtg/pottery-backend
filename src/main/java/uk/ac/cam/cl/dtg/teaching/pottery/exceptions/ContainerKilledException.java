package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class ContainerKilledException extends Exception {

	private static final long serialVersionUID = 1L;
	private long executionTimeMs;

	public ContainerKilledException(String message, long executionTimeMs) {
		super(message);
		this.executionTimeMs = executionTimeMs;
	}

	public long getExecutionTimeMs() {
		return executionTimeMs;
	}

}
