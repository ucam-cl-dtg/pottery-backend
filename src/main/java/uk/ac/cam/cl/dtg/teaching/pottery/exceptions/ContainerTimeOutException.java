package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class ContainerTimeOutException extends Exception {

	private static final long serialVersionUID = 1L;
	private long executionTimeMs;

	public ContainerTimeOutException(String message, long executionTimeMs) {
		super(message);
		this.executionTimeMs = executionTimeMs;
	}

	public long getExecutionTimeMs() {
		return executionTimeMs;
	}

}
