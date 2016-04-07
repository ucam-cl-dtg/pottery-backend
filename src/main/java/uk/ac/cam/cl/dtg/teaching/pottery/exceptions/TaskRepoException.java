package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class TaskRepoException extends Exception {

	private static final long serialVersionUID = 1L;

	public TaskRepoException() {
	}

	public TaskRepoException(String message) {
		super(message);
	}

	public TaskRepoException(Throwable cause) {
		super(cause);
	}

	public TaskRepoException(String message, Throwable cause) {
		super(message, cause);
	}

	public TaskRepoException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
