package uk.ac.cam.cl.dtg.teaching.pottery.containers;

public interface StepResponse<T> {

	public T getResponse();
	
	public boolean isSuccess();
	
	public String getFailMessage();
	
}
