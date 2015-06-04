package uk.ac.cam.cl.dtg.teaching.pottery.temp;

public class ValidationStep {
	
	private String criterion;
	private String result;
	private String[] references;
	
	public ValidationStep(String criterion, String result, String... references) {
		this.criterion = criterion;
		this.result = result;
		this.references = references;
	}
	
	public ValidationStep() {
		
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
