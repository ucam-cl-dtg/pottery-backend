package uk.ac.cam.cl.dtg.teaching.pottery.dto;

public class HarnessStep {

	public static final String TYPE_MESSAGE = "MESSAGE";
	public static final String TYPE_MEASUREMENT = "MEASUREMENT";
	public static final String TYPE_STATE = "STATE";
	
	public static final String MESSAGE_COMPLETED = "Completed";
	
	private String type;
	private String message;
	private Object actual;
	private String id;
	
	public HarnessStep() {}	
	
	public static HarnessStep newMessage(String message) {
		HarnessStep l = new HarnessStep();
		l.message = message;
		l.type = TYPE_MESSAGE;
		return l;
	}
	
	public static HarnessStep newMeasurement(String id, String message, Object actual) {
		HarnessStep l = new HarnessStep();
		l.id = id;
		l.message = message;
		l.actual = actual;
		l.type = TYPE_MEASUREMENT;
		return l;
	}
	
	public static HarnessStep newState(String message, Object actual) {
		HarnessStep l = new HarnessStep();
		l.message = message;
		l.actual = actual;
		l.type = TYPE_STATE;
		return l;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Object getActual() {
		return actual;
	}

	public void setActual(Object actual) {
		this.actual = actual;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
		
}