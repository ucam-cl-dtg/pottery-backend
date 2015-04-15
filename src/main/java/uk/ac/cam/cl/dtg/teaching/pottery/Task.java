package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.Set;

public class Task {

	private String taskId;
	
	private String description;
	
	private Set<Criteria> criteria;
	
	private Code skeletonCode;
	
	private Set<Binary> tests;
	
	public Task() {	}

	

	public Task(String taskId, String description) {
		super();
		this.taskId = taskId;
		this.description = description;
	}



	public String getTaskId() {
		return taskId;
	}



	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}



	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Set<Criteria> getCriteria() {
		return criteria;
	}

	public void setCriteria(Set<Criteria> criteria) {
		this.criteria = criteria;
	}

	public Code getSkeletonCode() {
		return skeletonCode;
	}

	public void setSkeletonCode(Code skeletonCode) {
		this.skeletonCode = skeletonCode;
	}

	public Set<Binary> getTests() {
		return tests;
	}

	public void setTests(Set<Binary> tests) {
		this.tests = tests;
	}
	
	

}
