package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class ContainerExecutionException extends Exception {

	private static final long serialVersionUID = 1L;

	public ContainerExecutionException() {
	}

	public ContainerExecutionException(String message) {
		super(message);
	}

	public ContainerExecutionException(Throwable cause) {
		super(cause);
	}

	public ContainerExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

	public ContainerExecutionException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
