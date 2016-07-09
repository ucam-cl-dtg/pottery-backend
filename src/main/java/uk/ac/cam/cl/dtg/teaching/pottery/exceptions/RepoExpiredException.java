package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class RepoExpiredException extends Exception {

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
