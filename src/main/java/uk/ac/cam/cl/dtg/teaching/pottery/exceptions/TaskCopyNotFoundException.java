package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

import uk.ac.cam.cl.dtg.teaching.exceptions.HttpStatusCode404;

public class TaskCopyNotFoundException extends Exception implements HttpStatusCode404 {

	private static final long serialVersionUID = -4601264504632864676L;

	public TaskCopyNotFoundException() {
	}

	public TaskCopyNotFoundException(String message) {
		super(message);
	}

	public TaskCopyNotFoundException(Throwable cause) {
		super(cause);
	}

	public TaskCopyNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public TaskCopyNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
