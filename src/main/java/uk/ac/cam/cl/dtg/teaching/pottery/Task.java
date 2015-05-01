package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Task {

	/**
	 * Algorithms & Data Structures: Tests the ability of the developer to compose algorithms and design appropriate data structures to solve the problem set out in the test.
	 */
	public static final String TYPE_ALGORITHM = "ALGORITHMS";
	
	/**
	 * Design Approaches: Evaluates the logical flow of solution code and appropriate design features of the solution (e.g. ???)
	 */
	public static final String TYPE_DESIGN = "DESIGN";
			
	
	/**	
	 * Black Box Testing: Tests the developer's ability to test a solution without knowing or being able to review the underlying source code.
	 */
	public static final String TYPE_BLACKBOX = "BLACKBOX";
	
	/**
	 * Unit Testing: Assesses the ability of the developer to write unit tests on pre-existing source code and or source code that they have themselves written.
	 */
	public static final String TYPE_UNITTEST = "UNITTEST";
	
	/**
	 * I/O Management: Evaluates the developers ability to implement strategies that result in appropriate I/O activity in a test solution.
	 */
	public static final String TYPE_IO = "IO";
	
	/**
	 * Cache & Memory Management: Evaluates the developers ability to implement strategies that result in appropriate cache and memory usage approaches in a test solution.
	 */
	public static final String TYPE_MEMORY = "MEMORY";
	
	/** 
	 * Using Existing APIs & Libraries:	Test the ability of a developer to appropriately exploit existing libraries and APIs to achieve the required test solution.
	 */
	public static final String TYPE_LIBRARY = "LIBRARY";
	
	/**
	 * 	Debugging: Assesses a developers ability in debugging existing code that has a series of known issues that must be fixed for it to function correctly.
	 */
	public static final String TYPE_DEBUGGING = "DEBUGGING";
	
	private String taskId;
	
	private String type;
	
	private String description;
	
	private Set<Criterion> criteria;
	
	private Code skeletonCode;
	
	private Set<Binary> tests;
	
	public Task() {	}

	

	public Task(String taskId, String description) {
		super();
		this.taskId = taskId;
		this.description = description;
	}

	public Task withCriteria(Criterion... criteria) {
		this.criteria = new HashSet<Criterion>(Arrays.asList(criteria));
		return this;
	}

	public String getTaskId() {
		return taskId;
	}



	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public Task withTaskId(String taskId) {
		setTaskId(taskId);
		return this;
	}
	

	public String getType() {
		return type;
	}



	public void setType(String type) {
		this.type = type;
	}



	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Task withDescription(String description) {
		setDescription(description);
		return this;
	}
	
	public Set<Criterion> getCriteria() {
		return criteria;
	}

	public void setCriteria(Set<Criterion> criteria) {
		this.criteria = criteria;
	}

	public Code getSkeletonCode() {
		return skeletonCode;
	}

	public void setSkeletonCode(Code skeletonCode) {
		this.skeletonCode = skeletonCode;
	}
	
	public Task withSkeletonCode(Code skeletonCode) {
		setSkeletonCode(skeletonCode);
		return this;
	}

	public Set<Binary> getTests() {
		return tests;
	}

	public void setTests(Set<Binary> tests) {
		this.tests = tests;
	}
	
	

}
