package uk.ac.cam.cl.dtg.teaching.pottery.dto;

public class ValidationStep {
	
	private String criterion;
	private String result;
	private boolean acceptable;
	private String[] references;
	
	public ValidationStep(String criterion, String result, boolean acceptable, String... references) {
		this.criterion = criterion;
		this.result = result;
		this.acceptable = acceptable;
		this.references = references;
	}
	
	public ValidationStep() {
		
	}	
	
	public boolean isAcceptable() {
		return acceptable;
	}

	public void setAcceptable(boolean acceptable) {
		this.acceptable = acceptable;
	}

	public String getCriterion() {
		return criterion;
	}

	public void setCriterion(String criterion) {
		this.criterion = criterion;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public String[] getReferences() {
		return references;
	}

	public void setReferences(String[] references) {
		this.references = references;
	}
	
	
	
}
