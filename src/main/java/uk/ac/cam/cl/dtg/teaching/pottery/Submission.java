package uk.ac.cam.cl.dtg.teaching.pottery;

import java.sql.SQLException;
import java.util.List;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;

public class Submission {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_COMPLETE = "COMPLETE";
	
	private int submissionId;
	private String repoId;
	private String tag;
	private boolean compilationSuccess;
	private String compilationResponse;
	private boolean harnessSuccess;
	private String harnessResponse;
	private boolean validationSuccess;
	private String validationResponse;
	
	private String status = STATUS_PENDING;
	
	public Submission() {}
	
	public Submission(String repoId, String tag) {
		this.repoId = repoId;
		this.tag = tag;
	}	

	public int getSubmissionId() {
		return submissionId;
	}

	public void setSubmissionId(int submissionId) {
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

	public boolean isCompilationSuccess() {
		return compilationSuccess;
	}

	public void setCompilationSuccess(boolean compilationSuccess) {
		this.compilationSuccess = compilationSuccess;
	}

	public String getCompilationResponse() {
		return compilationResponse;
	}

	public void setCompilationResponse(String compilationResponse) {
		this.compilationResponse = compilationResponse;
	}

	public boolean isHarnessSuccess() {
		return harnessSuccess;
	}

	public void setHarnessSuccess(boolean harnessSuccess) {
		this.harnessSuccess = harnessSuccess;
	}

	public String getHarnessResponse() {
		return harnessResponse;
	}

	public void setHarnessResponse(String harnessResponse) {
		this.harnessResponse = harnessResponse;
	}

	public boolean isValidationSuccess() {
		return validationSuccess;
	}

	public void setValidationSuccess(boolean validationSuccess) {
		this.validationSuccess = validationSuccess;
	}

	public String getValidationResponse() {
		return validationResponse;
	}

	public void setValidationResponse(String validationResponse) {
		this.validationResponse = validationResponse;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void insert(QueryRunner q) throws SQLException {
		
	}
	
	public void update(QueryRunner q) {
	}
	
	public static Submission getByRepoIdAndTag(String repoId, String tag, QueryRunner q) throws SQLException {
		return q.query("select * from submissions where repoid =? and tag = ?", new BeanHandler<>(Submission.class),repoId,tag);
	}
	
	public static List<Submission> getPending(QueryRunner q) throws SQLException {
		return q.query("SELECT * from submissions where status = ?",new BeanListHandler<Submission>(Submission.class),Submission.STATUS_PENDING);
	}

}