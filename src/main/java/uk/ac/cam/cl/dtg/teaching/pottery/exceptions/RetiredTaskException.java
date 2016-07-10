package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class RetiredTaskException extends Exception {

	private static final long serialVersionUID = 1L;

	public RetiredTaskException() {
	}

	public RetiredTaskException(String message) {
		super(message);
	}

	public RetiredTaskException(Throwable cause) {
		super(cause);
	}

	public RetiredTaskException(String message, Throwable cause) {
		super(message, cause);
	}

	public RetiredTaskException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
