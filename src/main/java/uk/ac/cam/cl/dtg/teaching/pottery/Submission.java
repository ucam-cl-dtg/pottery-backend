package uk.ac.cam.cl.dtg.teaching.pottery;


public class Submission {

	public static final String STATUS_RECEIVED = "RECEIVED";
	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_TESTING = "TESTING";
	public static final String STATUS_COMPLETE = "COMPLETE";
	
	private String submissionId;
	private String repoId;
	private String tag;
	private Result result;
	private String status;
	
	public Submission() {
	}

	public String getSubmissionId() {
		return submissionId;
	}

	public void setSubmissionId(String submissionId) {
		this.submissionId = submissionId;
	}

	public String getRepoId() {
		return repoId;
	}

	public void setRepoId(String repoId) {
		this.repoId = repoId;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public Result getResult() {
		return result;
	}

	public void setResult(Result result) {
		this.result = result;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}