package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class InvalidTaskSpecificationException extends Exception {

	private static final long serialVersionUID = -3171263573875750992L;

	public InvalidTaskSpecificationException() {
	}

	public InvalidTaskSpecificationException(String message) {
		super(message);
	}

	public InvalidTaskSpecificationException(Throwable cause) {
		super(cause);
	}

	public InvalidTaskSpecificationException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidTaskSpecificationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
