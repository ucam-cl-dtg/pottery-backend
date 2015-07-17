package uk.ac.cam.cl.dtg.teaching.pottery;

import uk.ac.cam.cl.dtg.teaching.pottery.containers.ExecResponse;

import com.mongodb.QueryBuilder;


/**
 * The results of the testing
 * @author acr31
 *
 */
public class Result {

	private String _id;
	
	private ExecResponse compilationResult;
	private ExecResponse harnessResult;
	private ExecResponse validationResult;
	
	private String repoId;
	private String submissionTag;
	
	public Result(String repoId, String submissionTag) {
		this.repoId = repoId;
		this.submissionTag= submissionTag;
	}
	
	public String get_id() {
		return _id;
	}

	public void set_id(String _id) {
		this._id = _id;
	}



	public ExecResponse getCompilationResult() {
		return compilationResult;
	}



	public void setCompilationResult(ExecResponse compilationResult) {
		this.compilationResult = compilationResult;
	}



	public ExecResponse getHarnessResult() {
		return harnessResult;
	}



	public void setHarnessResult(ExecResponse harnessResult) {
		this.harnessResult = harnessResult;
	}



	public ExecResponse getValidationResult() {
		return validationResult;
	}



	public void setValidationResult(ExecResponse validationResult) {
		this.validationResult = validationResult;
	}



	public String getRepoId() {
		return repoId;
	}



	public void setRepoId(String repoId) {
		this.repoId = repoId;
	}



	public String getSubmissionTag() {
		return submissionTag;
	}



	public void setSubmissionTag(String submissionTag) {
		this.submissionTag = submissionTag;
	}



	public static Result getByRepoIdAndSubmissionTag(String repoId,String submissionTag, Database database) {
		return database.getCollection(Result.class).findOne(QueryBuilder.start("repoId").is(repoId).and("submissionTag").is(submissionTag).get());
	}

	public void insert(Database database) {
		database.getCollection(Result.class).insert(this);
	}
	
}
