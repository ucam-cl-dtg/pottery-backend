package uk.ac.cam.cl.dtg.teaching.pottery;

import com.mongodb.QueryBuilder;

public class Repo {

	private String _id;
	private String repoId;
	private String taskId;
	
	public Repo(String repoId, String taskId) {
		super();
		this.repoId = repoId;
		this.taskId = taskId;
	}
	
	
	public Repo() {
		super();
	}


	public String get_id() {
		return _id;
	}
	public void set_id(String _id) {
		this._id = _id;
	}
	public String getRepoId() {
		return repoId;
	}
	public void setRepoId(String repoId) {
		this.repoId = repoId;
	}
	public String getTaskId() {
		return taskId;
	}
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
	
	public static Repo getByRepoId(String repoId, Database database) {
		return database.getCollection(Repo.class).findOne(QueryBuilder.start("repoId").is(repoId).get());
	}
	
	public void insert(Database database) {
		database.getCollection(Repo.class).insert(this);
	}
}
