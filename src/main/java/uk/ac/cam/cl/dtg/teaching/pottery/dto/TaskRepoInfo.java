package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.util.LinkedList;
import java.util.List;

public class TaskRepoInfo implements Comparable<TaskRepoInfo> {

	private String taskRepoId;
	private List<TaskInfo> registeredTasks = new LinkedList<>();
	
	public TaskRepoInfo(String taskRepoId) {
		super();
		this.taskRepoId = taskRepoId;
	}
	public String getTaskRepoId() {
		return taskRepoId;
	}
	public void setTaskRepoId(String taskRepoId) {
		this.taskRepoId = taskRepoId;
	}
	public List<TaskInfo> getRegisteredTasks() {
		return registeredTasks;
	}
	public void setRegisteredTasks(List<TaskInfo> registeredTasks) {
		this.registeredTasks = registeredTasks;
	}
	
	@Override
	public int compareTo(TaskRepoInfo o) {
		return taskRepoId.compareTo(o.taskRepoId);
	}
	
	
	
	
}
