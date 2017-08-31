/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015 Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

	
	@JsonCreator
	public TestStep(
			@JsonProperty("testSteps") List<String> testSteps, 
			@JsonProperty("testSummary") String testSummary, 
			@JsonProperty("measurements") List<InterpretedMeasurement> measurements,
			@JsonProperty("errorSummary") String errorSummary, 
			@JsonProperty("errorDetail") String errorDetail) {
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
