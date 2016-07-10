package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

import uk.ac.cam.cl.dtg.teaching.exceptions.HttpStatusCode403;

public class RepoExpiredException extends Exception implements HttpStatusCode403 {

	private static final long serialVersionUID = 1L;

	public RepoExpiredException() {
	}

	public RepoExpiredException(String message) {
		super(message);
	}

	public RepoExpiredException(Throwable cause) {
		super(cause);
	}

	public RepoExpiredException(String message, Throwable cause) {
		super(message, cause);
	}

	public RepoExpiredException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
