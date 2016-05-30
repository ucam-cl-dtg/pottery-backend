package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;

public class ContainerTimeOutException extends Exception {

	private static final long serialVersionUID = 1L;

	public ContainerTimeOutException() {
	}

	public ContainerTimeOutException(String message) {
		super(message);
	}

	public ContainerTimeOutException(Throwable cause) {
		super(cause);
	}

	public ContainerTimeOutException(String message, Throwable cause) {
		super(message, cause);
	}

	public ContainerTimeOutException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
