package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class TaskNotAvailableException extends Exception {
	private static final long serialVersionUID = 1L;

	public TaskNotAvailableException() {
	}

	public TaskNotAvailableException(String message) {
		super(message);
	}

	public TaskNotAvailableException(Throwable cause) {
		super(cause);
	}

	public TaskNotAvailableException(String message, Throwable cause) {
		super(message, cause);
	}

	public TaskNotAvailableException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
