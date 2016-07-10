package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class TaskStorageException extends Exception {

	private static final long serialVersionUID = 7151409283369796541L;

	public TaskStorageException() {
	}

	public TaskStorageException(String message) {
		super(message);
	}

	public TaskStorageException(Throwable cause) {
		super(cause);
	}

	public TaskStorageException(String message, Throwable cause) {
		super(message, cause);
	}

	public TaskStorageException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
