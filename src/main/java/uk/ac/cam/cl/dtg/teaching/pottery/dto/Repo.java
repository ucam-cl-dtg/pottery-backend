package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.sql.SQLException;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;

public class Repo {
	
	private String repoId;
	private String taskId;
	private boolean release;
	
	public Repo() {}
	
	public Repo(String repoId, String taskId, boolean release) {
		super();
		this.repoId = repoId;
		this.taskId = taskId;
		this.release = release;
	}
	
	
	public void setRepoId(String repoId) {
		this.repoId = repoId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public void setRelease(boolean release) {
		this.release = release;
	}

	public String getRepoId() {
		return repoId;
	}

	public String getTaskId() {
		return taskId;
	}
	
	public boolean isRelease() {
		return release;
	}

	public static Repo getByRepoId(String repoId, QueryRunner q) throws SQLException {
		return q.query("SELECT * from repos where repoid=?",new BeanHandler<Repo>(Repo.class),repoId);
	}
	
	public void insert(QueryRunner q) throws SQLException {
		q.update("INSERT INTO repos(repoid,taskid,release) values (?,?,?)",repoId,taskId,release);
	}
}
