package uk.ac.cam.cl.dtg.teaching.pottery;

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
	
	private String name;
	
	private Set<Criterion> criteria;
	
	private String image;
	
	public Task() {	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<Criterion> getCriteria() {
		return criteria;
	}

	public void setCriteria(Set<Criterion> criteria) {
		this.criteria = criteria;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}


	
}
