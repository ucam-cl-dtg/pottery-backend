package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;

public class RepoInfo {
	
	private String repoId;
	private String taskId;
	private boolean usingTestingVersion;
	private Date expiryDate;
	
	public RepoInfo() {}
	
	public RepoInfo(String repoId, String taskId, boolean usingTestingVersion, Date expiryDate) {
		super();
		this.repoId = repoId;
		this.taskId = taskId;
		this.usingTestingVersion = usingTestingVersion;
		this.expiryDate = expiryDate;
	}
	
	
	public Date getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(Date expiryDate) {
		this.expiryDate = expiryDate;
	}

	public void setRepoId(String repoId) {
		this.repoId = repoId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getRepoId() {
		return repoId;
	}

	public String getTaskId() {
		return taskId;
	}
	
	public boolean isUsingTestingVersion() {
		return usingTestingVersion;
	}

	public void setUsingTestingVersion(boolean usingTestingVersion) {
		this.usingTestingVersion = usingTestingVersion;
	}

	public static RepoInfo getByRepoId(String repoId, QueryRunner q) throws SQLException {
		return q.query("SELECT * from repos where repoid=?",new BeanHandler<RepoInfo>(RepoInfo.class),repoId);
	}
	
	public void insert(QueryRunner q) throws SQLException {
		q.update("INSERT INTO repos(repoid,taskid,using_testing_version,expiryDate) values (?,?,?,?)",
				repoId,taskId,usingTestingVersion,new Timestamp(expiryDate.getTime()));
		
	}
}
