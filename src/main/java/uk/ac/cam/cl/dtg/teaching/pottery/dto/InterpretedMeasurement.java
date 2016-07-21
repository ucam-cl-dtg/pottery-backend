package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.util.Map;

import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Interpretation;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Measurement;

public class InterpretedMeasurement {

	private String criterion;
	private String measurement;
	private String result;
	private String explanation;
	
	public InterpretedMeasurement(String criterion, String measurement, String result, String explanation) {
		super();
		this.criterion = criterion;
		this.measurement = measurement;
		this.result = result;
		this.explanation = explanation;
	}

	public InterpretedMeasurement(Measurement m,Map<String,Interpretation> imap) {
		super();
		this.criterion = m.getCriterion();
		this.measurement = m.getMeasurement();
		if (imap != null && imap.containsKey(m.getId())) {
			Interpretation i = imap.get(m.getId());
			this.result = i.getResult();
			this.explanation = i.getExplanation();
		}
	}
	
	public String getCriterion() {
		return criterion;
	}

	public String getMeasurement() {
		return measurement;
	}

	public String getResult() {
		return result;
	}

	public String getExplanation() {
		return explanation;
	}
	
	
	
}
