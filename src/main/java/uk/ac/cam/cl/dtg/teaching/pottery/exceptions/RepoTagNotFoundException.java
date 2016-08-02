package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

import uk.ac.cam.cl.dtg.teaching.exceptions.HttpStatusCode404;

public class RepoTagNotFoundException extends Exception implements HttpStatusCode404 {

	private static final long serialVersionUID = 1L;

	public RepoTagNotFoundException() {
	}

	public RepoTagNotFoundException(String message) {
		super(message);
	}

	public RepoTagNotFoundException(Throwable cause) {
		super(cause);
	}

	public RepoTagNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public RepoTagNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
