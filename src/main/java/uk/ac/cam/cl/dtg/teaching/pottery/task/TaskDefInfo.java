package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;

public class TaskDefInfo {

	private String taskId;
	
	private String registeredTag;
	
	private String testingCopyId;
	
	private String registeredCopyId;
	
	private boolean retired;

	public TaskDefInfo() {}
	
	public TaskDefInfo(String taskId, String registeredTag, String testingCopyId, String registeredCopyId,
			boolean retired) {
		super();
		this.taskId = taskId;
		this.registeredTag = registeredTag;
		this.testingCopyId = testingCopyId;
		this.registeredCopyId = registeredCopyId;
		this.retired = retired;
	}


	public String getTestingCopyId() {
		return testingCopyId;
	}

	public void setTestingCopyId(String testingCopyId) {
		this.testingCopyId = testingCopyId;
	}

	public String getRegisteredCopyId() {
		return registeredCopyId;
	}

	public void setRegisteredCopyId(String registeredCopyId) {
		this.registeredCopyId = registeredCopyId;
	}

	public boolean isRetired() {
		return retired;
	}


	public void setRetired(boolean retired) {
		this.retired = retired;
	}


	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getRegisteredTag() {
		return registeredTag;
	}

	public void setRegisteredTag(String registeredTag) {
		this.registeredTag = registeredTag;
	}

	public static TaskDefInfo getByTaskId(String taskId, QueryRunner q) throws SQLException {
		return q.query("SELECT * from tasks where taskid=?",new BeanHandler<>(TaskDefInfo.class),taskId);
	}
	
	public void insert(QueryRunner q) throws SQLException {
		q.update("INSERT INTO tasks(taskid,registeredtag,retired) values (?,?,?)",taskId,registeredTag,retired);
	}

	public static List<String> getAllTaskIds(QueryRunner q) throws SQLException {
		return q.query("Select taskId from tasks", new ResultSetHandler<List<String>>() {
			@Override
			public List<String> handle(ResultSet rs) throws SQLException {
				List<String> result = new ArrayList<String>();
				while(rs.next()) {
					result.add(rs.getString(1));
				}
				return result;
			}
		});
	}
	
	public static void updateRegisteredCopy(String taskId, String tag, String copyId, QueryRunner q) throws SQLException {
		q.update("UPDATE tasks set registeredtag=?,registeredCopyId=? where taskid = ?",tag,copyId,taskId);
	}
	

	public static void updateTestingCopy(String taskId, String copyId, QueryRunner q) throws SQLException {
		q.update("UPDATE tasks set testingCopyId=? where taskid = ?",copyId,taskId);
	}

	@Override
	public String toString() {
		return "TaskDefInfo [taskId=" + taskId + ", registeredTag=" + registeredTag + ", testingCopyId=" + testingCopyId
				+ ", registeredCopyId=" + registeredCopyId + ", retired=" + retired + "]";
	}
	
	

}
