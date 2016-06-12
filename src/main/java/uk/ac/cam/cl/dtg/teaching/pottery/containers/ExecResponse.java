package uk.ac.cam.cl.dtg.teaching.pottery.containers;

public class ExecResponse {
	
	private boolean success;
	private String response;
	private long executionTimeMs;
	
	public ExecResponse() {}
	
	public ExecResponse(boolean success, String response, long executionTimeMs) {
		super();
		this.success = success;
		this.response = response;
		this.executionTimeMs = executionTimeMs;
	}
	
	
	public long getExecutionTimeMs() {
		return executionTimeMs;
	}

	public void setExecutionTimeMs(long executionTimeMs) {
		this.executionTimeMs = executionTimeMs;
	}

	public boolean isSuccess() {
		return success;
	}
	public void setSuccess(boolean success) {
		this.success = success;
	}
	public String getResponse() {
		return response;
	}
	public void setResponse(String response) {
		this.response = response;
	}
	
}
