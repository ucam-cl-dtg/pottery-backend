package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.LinkedList;
import java.util.List;

/**
 * Class for tracking the progress that a candidate has made through the tests.  Keeps track of all their submissions, best results etc.
 * @author acr31
 *
 */
public class Progress {

	private String progressId;
	private String taskId;
	
	private List<Submission> submissions;
	
	public Progress() {
		submissions = new LinkedList<Submission>();
	}

	public String getProgressId() {
		return progressId;
	}

	public void setProgressId(String progressId) {
		this.progressId = progressId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public void addSubmission(Submission submission) {
		submissions.add(submission);
	}
	
	
}
