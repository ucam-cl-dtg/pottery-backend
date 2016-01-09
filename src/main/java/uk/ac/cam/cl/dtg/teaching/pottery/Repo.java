package uk.ac.cam.cl.dtg.teaching.pottery;

import java.sql.SQLException;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;

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
	
	public static Repo getByRepoId(String repoId, QueryRunner q) throws SQLException {
		return q.query("SELECT * from repos where repoid=?",new BeanHandler<Repo>(Repo.class),repoId);
	}
	
	public void insert(QueryRunner q) throws SQLException {
		q.update("INSERT INTO repos(repoid,taskid) values (?,?)",repoId,taskId);
	}
}
