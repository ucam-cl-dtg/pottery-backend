package uk.ac.cam.cl.dtg.teaching.pottery.dto;

public class TaskTestResponse {

	private boolean success;
	private String message;

	public TaskTestResponse(boolean success, String message) {
		super();
		this.success = success;
		this.message = message;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	

	
}
