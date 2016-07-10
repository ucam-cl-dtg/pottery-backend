package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

import uk.ac.cam.cl.dtg.teaching.exceptions.HttpStatusCode404;

public class TaskNotFoundException extends Exception implements HttpStatusCode404 {

	private static final long serialVersionUID = 1L;

	public TaskNotFoundException() {
		super();
	}

	public TaskNotFoundException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public TaskNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public TaskNotFoundException(String message) {
		super(message);
	}

	public TaskNotFoundException(Throwable cause) {
		super(cause);
	}

}
