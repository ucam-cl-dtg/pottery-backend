package uk.ac.cam.cl.dtg.teaching.pottery.task;

/**
 * DTO for tracking information about the progress of creating a TaskCopy
 * @author acr31
 *
 */
public class BuilderInfo {
	
	public static final String STATUS_NOT_STARTED = "NOT_STARTED";
	public static final String STATUS_SCHEDULED = "SCHEDULED";
	public static final String STATUS_COPYING_FILES = "COPYING_FILES";
	public static final String STATUS_COMPILING_TEST = "COMPILING_TEST";
	public static final String STATUS_COMPILING_SOLUTION = "COMPILING_SOLUTION";
	public static final String STATUS_TESTING_SOLUTION = "TESTING_SOLUTION";
	public static final String STATUS_SUCCESS = "SUCCESS";
	public static final String STATUS_FAILURE = "FAILURE";
	
	/**
	 * The SHA1 from the parent repo that we are copying
	 */
	private final String sha1;
	
	/**
	 * The status of the copy. This is updated and read from multiple threads so its volatile. 
	 */
	private volatile String status;
	
	/**
	 * Will contain an exception object if a problem occurred during building. This is updated and read from multiple threads so its volatile.
	 */
	private volatile Exception exception;
	
	public BuilderInfo(String sha1) {
		super();
		this.sha1 = sha1;
		this.status = BuilderInfo.STATUS_NOT_STARTED;
		this.exception = null;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Exception getException() {
		return exception;
	}

	public void setException(Exception exception) {
		this.exception = exception;
		this.status = BuilderInfo.STATUS_FAILURE;
	}

	public String getSha1() {
		return sha1;
	}	
}
