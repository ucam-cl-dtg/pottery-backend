package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.util.List;

import uk.ac.cam.cl.dtg.teaching.pottery.containers.StepResponse;

public class HarnessResponse implements StepResponse<List<HarnessStep>>{

	private boolean success;
	private List<HarnessStep> response;
	private String failMessage;
	
	public HarnessResponse() {}
	
	public HarnessResponse(String failMessage) {
		this.success =false;
		this.failMessage = failMessage;
	}
	
	public HarnessResponse(boolean success, List<HarnessStep> response, String failMessage) {
		super();
		this.success = success;
		this.response = response;
		this.failMessage = failMessage;
	}

	public List<HarnessStep> getResponse() {
		return response;
	}

	public String getFailMessage() {
		return failMessage;
	}

	public boolean isSuccess() {
		return success;
	}

}
