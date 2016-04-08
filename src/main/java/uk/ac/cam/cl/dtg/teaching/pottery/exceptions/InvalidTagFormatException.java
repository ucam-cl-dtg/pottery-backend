package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class InvalidTagFormatException extends Exception {

	private static final long serialVersionUID = 1L;

	public InvalidTagFormatException() {
	}

	public InvalidTagFormatException(String message) {
		super(message);
	}

	public InvalidTagFormatException(Throwable cause) {
		super(cause);
	}

	public InvalidTagFormatException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidTagFormatException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
