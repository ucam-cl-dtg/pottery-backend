package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

import uk.ac.cam.cl.dtg.teaching.exceptions.HttpStatusCode404;

public class SubmissionNotFoundException extends Exception implements HttpStatusCode404 {

	private static final long serialVersionUID = 1L;

	public SubmissionNotFoundException() {
	}

	public SubmissionNotFoundException(String message) {
		super(message);
	}

	public SubmissionNotFoundException(Throwable cause) {
		super(cause);
	}

	public SubmissionNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public SubmissionNotFoundException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
