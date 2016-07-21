package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessPart;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Interpretation;

public class TestStep {

	/**
	 * A list of strings giving detailed information about what the test did. What objects did it create, what steps did it follow? Use <code> tags to delimit code for formatting.
	 * For example: 
	 * 
	 * Created an array <code>a</code> with 10 elements: <code>1.0</code>, <code>4.5</code>, <code>6.6</code>, <code>2.1</code>, <code>6.0</code>, <code>4.33</code>, <code>10.0</code>, <code>56.3</code>, <code>100.4</code>, <code>43.4</code>.
	 * Called <code>Average.mean(a)</code>
	 */
	private List<String> testSteps;
	
	/**
	 * A brief summary of the test that describes the kind of thing tested rather than the detail of how it was tested. Use <code> tags to delimit code for formatting.
	 * 
	 *  For example:
	 *  
	 *  Calculated the mean of small arrays containing values in the range of 0 to 1000
	 *  
	 *  Or:
	 *  
	 *  Calculated the mean of small arrays containing a mixture of very small and very large values
	 */
	private String testSummary;
		
	private List<InterpretedMeasurement> measurements;
		
	/**
	 * If non-null then an error occurred and this is a short summary 
	 */
	private String errorSummary;
	
	/**
	 * If non-null then an error occurred and this is a detailed breakdown of the error. Use <code> tags to delimit code, and <ul>, <li> for listing elements (e.g. stack trace)
	 */
	private String errorDetail;

	public TestStep(List<String> testSteps, String testSummary, List<InterpretedMeasurement> measurements,
			String errorSummary, String errorDetail) {
		super();
		this.testSteps = testSteps;
		this.testSummary = testSummary;
		this.measurements = measurements;
		this.errorSummary = errorSummary;
		this.errorDetail = errorDetail;
	}

	public TestStep(HarnessPart p, Map<String,Interpretation> i) {
		this.testSteps = p.getTestSteps();
		this.testSummary = p.getTestSummary();
		this.measurements = p.getMeasurements().stream().map(m -> new InterpretedMeasurement(m,i)).collect(Collectors.toList());
		this.errorSummary = p.getErrorSummary();
		this.errorDetail = p.getErrorDetail();
	}
	
	
	public List<String> getTestSteps() {
		return testSteps;
	}

	public String getTestSummary() {
		return testSummary;
	}

	public List<InterpretedMeasurement> getMeasurements() {
		return measurements;
	}

	public String getErrorSummary() {
		return errorSummary;
	}

	public String getErrorDetail() {
		return errorDetail;
	}
	
	
	
}
