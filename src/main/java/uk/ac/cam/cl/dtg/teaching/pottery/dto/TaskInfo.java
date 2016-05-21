package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.annotations.ApiModelProperty;

import uk.ac.cam.cl.dtg.teaching.pottery.Criterion;

public class TaskInfo {

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
	
	@ApiModelProperty("The unique identifier for this task")
	private String taskId;
	
	@ApiModelProperty("The primary skill tested by this task")
	private String type;
	
	@ApiModelProperty("User readable name for the test")
	private String name;
	
	@ApiModelProperty("The set of types of result that will come back from testing this task")
	private Set<Criterion> criteria;
	
	@ApiModelProperty("The name of the docker image to use when testing this image")
	private String image;
	
	@ApiModelProperty("Test difficulty (values to be determined)")
	private String difficulty;
	
	@ApiModelProperty("The recommended time in minutes to allocate to this task")
	private int recommendedTimeMinutes;
	
	@ApiModelProperty("The programming language being tested (currently only java)")
	private String language;
	
	@ApiModelProperty("The problem statement as an HTML fragment")
	private String problemStatement;
	
	@JsonCreator
	public TaskInfo(@JsonProperty("type") String type, 
				@JsonProperty("name") String name, 
				@JsonProperty("criteria") Set<Criterion> criteria, 
				@JsonProperty("image") String image, 
				@JsonProperty("difficulty") String difficulty,
				@JsonProperty("recommendedTimeMinutes") int recommendedTimeMinutes, 
				@JsonProperty("language") String language, 
				@JsonProperty("problemStatement") String problemStatement) {
		super();
		this.type = type;
		this.name = name;
		this.criteria = criteria;
		this.image = image;
		this.difficulty = difficulty;
		this.recommendedTimeMinutes = recommendedTimeMinutes;
		this.language = language;
		this.problemStatement = problemStatement;
	}


	public String getTaskId() {
		return taskId;
	}


	public String getType() {
		return type;
	}


	public String getName() {
		return name;
	}


	public Set<Criterion> getCriteria() {
		return criteria;
	}


	public String getImage() {
		return image;
	}


	public String getDifficulty() {
		return difficulty;
	}


	public int getRecommendedTimeMinutes() {
		return recommendedTimeMinutes;
	}


	public String getLanguage() {
		return language;
	}


	public String getProblemStatement() {
		return problemStatement;
	}

	public static TaskInfo load(File taskDirectory) throws IOException {
		String taskId = taskDirectory.getName();
		ObjectMapper o = new ObjectMapper();
		TaskInfo t = o.readValue(new File(taskDirectory,"task.json"),TaskInfo.class);
		t.taskId = taskId;
		return t;
	}

}
