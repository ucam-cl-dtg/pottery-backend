package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class CriterionNotFoundException extends Exception {

	private static final long serialVersionUID = 1L;

	public CriterionNotFoundException() {
	}

	public CriterionNotFoundException(String message) {
		super(message);
	}

	public CriterionNotFoundException(Throwable cause) {
		super(cause);
	}

	public CriterionNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public CriterionNotFoundException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
