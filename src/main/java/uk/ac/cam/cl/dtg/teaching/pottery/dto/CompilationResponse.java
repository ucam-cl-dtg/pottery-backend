package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import uk.ac.cam.cl.dtg.teaching.pottery.containers.StepResponse;

public class CompilationResponse implements StepResponse<String> {
	private boolean success;
	private String failMessage;
	private String response;

	public CompilationResponse(boolean success, String message) {
		super();
		this.success = success;
		if (success) {
			this.response = message;
		}
		else {
			this.failMessage = message;
		}
	}

	public CompilationResponse() {
		super();
	}
	
	
	
	public CompilationResponse(boolean success, String failMessage, String response) {
		super();
		this.success = success;
		this.failMessage = failMessage;
		this.response = response;
	}

	public boolean isSuccess() {
		return success;
	}
	public void setSuccess(boolean success) {
		this.success = success;
	}
	public String getFailMessage() {
		return failMessage;
	}
	public void setFailMessage(String failMessage) {
		this.failMessage = failMessage;
	}
	public String getResponse() {
		return response;
	}
	public void setResponse(String response) {
		this.response = response;
	}	
	
}
