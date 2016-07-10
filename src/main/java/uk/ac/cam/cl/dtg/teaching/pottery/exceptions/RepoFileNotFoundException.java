package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

import uk.ac.cam.cl.dtg.teaching.exceptions.HttpStatusCode404;

public class RepoFileNotFoundException extends Exception implements HttpStatusCode404 {

	private static final long serialVersionUID = 863882595858722256L;

	public RepoFileNotFoundException() {
	}

	public RepoFileNotFoundException(String message) {
		super(message);
	}

	public RepoFileNotFoundException(Throwable cause) {
		super(cause);
	}

	public RepoFileNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public RepoFileNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
