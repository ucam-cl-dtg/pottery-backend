package uk.ac.cam.cl.dtg.teaching.pottery.containers;

public class ExecResponse {
	
	private boolean success;
	private String response;
	
	public ExecResponse() {}
	
	public ExecResponse(boolean success, String response) {
		super();
		this.success = success;
		this.response = response;
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
