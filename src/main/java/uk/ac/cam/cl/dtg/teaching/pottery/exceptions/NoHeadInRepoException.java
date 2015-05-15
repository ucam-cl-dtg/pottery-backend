package uk.ac.cam.cl.dtg.teaching.pottery.exceptions;


public class NoHeadInRepoException extends RepoException {

	private static final long serialVersionUID = 1L;

	public NoHeadInRepoException() {
	}

	public NoHeadInRepoException(String message) {
		super(message);
	}

	public NoHeadInRepoException(Throwable cause) {
		super(cause);
	}

	public NoHeadInRepoException(String message, Throwable cause) {
		super(message, cause);
	}

	public NoHeadInRepoException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
