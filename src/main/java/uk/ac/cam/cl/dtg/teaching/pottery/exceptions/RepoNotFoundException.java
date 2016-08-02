package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

import uk.ac.cam.cl.dtg.teaching.exceptions.HttpStatusCode404;

public class RepoNotFoundException extends Exception implements HttpStatusCode404 {

	private static final long serialVersionUID = 1L;

	public RepoNotFoundException() {
	}

	public RepoNotFoundException(String message) {
		super(message);
	}

	public RepoNotFoundException(Throwable cause) {
		super(cause);
	}

	public RepoNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public RepoNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
