package uk.ac.cam.cl.dtg.teaching.pottery;

public class Status {

	public static final String STATUS_RECEIVED = "RECEIVED";
	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_TESTING = "TESTING";
	public static final String STATUS_COMPLETE = "COMPLETE";

	private String status;
	
	public Status() {
		status = STATUS_RECEIVED;
	}

	public synchronized String getStatus() {
		return status;
	}

	public synchronized void setStatus(String status) {
		this.status = status;
	}

	public synchronized boolean isReceived() {
		return status.equals(STATUS_RECEIVED);
	}
	
	
	
}
