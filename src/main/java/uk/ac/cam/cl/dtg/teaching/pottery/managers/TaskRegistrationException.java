package uk.ac.cam.cl.dtg.teaching.pottery.managers;

public class TaskRegistrationException extends Exception {

	private static final long serialVersionUID = 1L;

	public TaskRegistrationException() {
	}

	public TaskRegistrationException(String message) {
		super(message);
	}

	public TaskRegistrationException(Throwable cause) {
		super(cause);
	}

	public TaskRegistrationException(String message, Throwable cause) {
		super(message, cause);
	}

	public TaskRegistrationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
