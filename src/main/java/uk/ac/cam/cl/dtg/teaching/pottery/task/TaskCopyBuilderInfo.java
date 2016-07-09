package uk.ac.cam.cl.dtg.teaching.pottery.task;

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;

public class TaskCopyBuilderInfo {
	private String sha1;
	private String status;
	private TaskCloneException exception;

	public TaskCopyBuilderInfo(String sha1) {
		super();
		this.sha1 = sha1;
		this.status = TaskCopyBuilder.STATUS_NOT_STARTED;
		this.exception = null;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public TaskCloneException getException() {
		return exception;
	}

	public void setException(TaskCloneException exception) {
		this.exception = exception;
		this.status = TaskCopyBuilder.STATUS_FAILURE;
	}

	public String getSha1() {
		return sha1;
	}

	public boolean isRunnable() {
		return 
				TaskCopyBuilder.STATUS_NOT_STARTED.equals(this.status) ||
				TaskCopyBuilder.STATUS_SUCCESS.equals(this.status) ||
				TaskCopyBuilder.STATUS_FAILURE.equals(this.status);
	}
	
	
}
