package uk.ac.cam.cl.dtg.teaching.pottery.task;

public class TaskCompilationStatus {
	private String status;

	private String message;
	
	public TaskCompilationStatus(String status, String message) {
		this.status = status;
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}

	public String getStatus() {
		return status;
	}
	
	
	
}
