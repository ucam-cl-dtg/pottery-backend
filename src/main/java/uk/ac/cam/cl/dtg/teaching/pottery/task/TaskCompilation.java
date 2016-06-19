package uk.ac.cam.cl.dtg.teaching.pottery.task;

public class TaskCompilation {

	private String requestedTag;
	
	private String status;

	private String message;
	
	public TaskCompilation(String requestedTag, String status, String message) {
		this.requestedTag = requestedTag;
		this.status = status;
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}

	public String getRequestedTag() {
		return requestedTag;
	}

	public String getStatus() {
		return status;
	}
	
	
	
}
