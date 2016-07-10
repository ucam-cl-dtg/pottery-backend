package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class SubmissionStorageException extends Exception {

	private static final long serialVersionUID = -2411010204004589140L;

	public SubmissionStorageException() {
	}

	public SubmissionStorageException(String message) {
		super(message);
	}

	public SubmissionStorageException(Throwable cause) {
		super(cause);
	}

	public SubmissionStorageException(String message, Throwable cause) {
		super(message, cause);
	}

	public SubmissionStorageException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
