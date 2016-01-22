package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;

public class Submission {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_COMPLETE = "COMPLETE";
	
	private Integer submissionId;
	private String repoId;
	private String tag;
	private CompilationResponse compilationResponse;
	private HarnessResponse harnessResponse;
	private ValidationResponse validationResponse;
	
	private String status = STATUS_PENDING;
	
	public Submission() {}
	
	public Submission(String repoId, String tag) {
		this.repoId = repoId;
		this.tag = tag;
	}	
	
	public Submission(Integer submissionId, String repoId, String tag) {
		super();
		this.submissionId = submissionId;
		this.repoId = repoId;
		this.tag = tag;
	}

	public int getSubmissionId() {
		return submissionId;
	}

	public String getRepoId() {
		return repoId;
	}

	public String getTag() {
		return tag;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public CompilationResponse getCompilationResponse() {
		return compilationResponse;
	}

	public void setCompilationResponse(CompilationResponse compilationResponse) {
		this.compilationResponse = compilationResponse;
	}

	public HarnessResponse getHarnessResponse() {
		return harnessResponse;
	}

	public void setHarnessResponse(HarnessResponse harnessResponse) {
		this.harnessResponse = harnessResponse;
	}

	public ValidationResponse getValidationResponse() {
		return validationResponse;
	}

	public void setValidationResponse(ValidationResponse validationResponse) {
		this.validationResponse = validationResponse;
	}

	public void setSubmissionId(Integer submissionId) {
		this.submissionId = submissionId;
	}

	public void insert(TransactionQueryRunner q) throws SQLException {
		if (submissionId != null) throw new SQLException("Submission already exists in database with submissionid="+submissionId);
		int s = Database.nextVal("seqsubmission", q);
		ObjectMapper mapper = new ObjectMapper();
		try {
			q.update("INSERT into submissions ("
					+ "submissionid,"
					+ "repoid,"
					+ "tag,"
					+ "status,"
					+ "compilationsuccess,"
					+ "compilationresponse,"
					+ "compilationfailmessage,"
					+ "harnesssuccess,"
					+ "harnessresponse,"
					+ "harnessfailmessage,"
					+ "validationsuccess,"
					+ "validationresponse,"
					+ "validationfailmessage) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
					s,
					repoId,
					tag,
					status,
					compilationResponse == null ? null : compilationResponse.isSuccess(),
					compilationResponse == null ? null : mapper.writeValueAsString(compilationResponse.getResponse()),
					compilationResponse == null ? null : compilationResponse.getFailMessage(),
					harnessResponse == null ? null : harnessResponse.isSuccess(),
					harnessResponse == null ? null : mapper.writeValueAsString(harnessResponse.getResponse()),
					harnessResponse == null ? null : harnessResponse.getFailMessage(),
					validationResponse == null ? null : validationResponse.isSuccess(),
					validationResponse == null ? null : mapper.writeValueAsString(validationResponse.getResponse()),
					validationResponse == null ? null : validationResponse.getFailMessage()
					);
		} catch (JsonProcessingException e) {
			throw new SQLException("Failed to serialise object",e);
		}
		q.commit();
		submissionId = s;
	}
	
	public void update(TransactionQueryRunner q) throws SQLException {
		ObjectMapper mapper = new ObjectMapper();
		try {
			q.update("update submissions set "
					+ "repoid=?,"
					+ "tag=?,"
					+ "status=?,"
					+ "compilationsuccess=?,"
					+ "compilationresponse=?,"
					+ "compilationfailmessage=?,"
					+ "harnesssuccess=?,"
					+ "harnessresponse=?,"
					+ "harnessfailmessage=?,"
					+ "validationsuccess=?,"
					+ "validationresponse=?,"
					+ "validationfailmessage=?"
					+ " where "
					+ "submissionid=?",
					repoId,
					tag,
					status,
					compilationResponse == null ? null :compilationResponse.isSuccess(),
					compilationResponse == null ? null :mapper.writeValueAsString(compilationResponse.getResponse()),
					compilationResponse == null ? null :compilationResponse.getFailMessage(),
					harnessResponse == null ? null : harnessResponse.isSuccess(),
					harnessResponse == null ? null : mapper.writeValueAsString(harnessResponse.getResponse()),
					harnessResponse == null ? null : harnessResponse.getFailMessage(),
					validationResponse == null ? null : validationResponse.isSuccess(),
					validationResponse == null ? null : mapper.writeValueAsString(validationResponse.getResponse()),
					validationResponse == null ? null : validationResponse.getFailMessage(),
					submissionId
					);
		} catch (JsonProcessingException e) {
			throw new SQLException("Failed to serialise object",e);
		}
		q.commit();
	}
	
	
	private static Submission resultSetToSubmission(ResultSet rs) throws SQLException {
		try {
			Submission s = new Submission(
					rs.getInt("submissionid"),
					rs.getString("repoid"),
					rs.getString("tag"));
			ObjectMapper o = new ObjectMapper();
			boolean compilationSuccess = rs.getBoolean("compilationSuccess");
			if (!rs.wasNull()) {
				s.setCompilationResponse(new CompilationResponse(
						compilationSuccess,
						rs.getString("compilationfailmessage"),
						rs.getString("compilationresponse")));
			}

			boolean harnessSuccess = rs.getBoolean("harnessSuccess");
			if (!rs.wasNull()) {
				s.setHarnessResponse(new HarnessResponse(
						harnessSuccess,
						o.readValue(rs.getString("harnessresponse"),new TypeReference<List<HarnessStep>>() {}),
						rs.getString("harnessfailmessage")));
			}

			boolean validationSuccess = rs.getBoolean("validationSuccess");
			if (!rs.wasNull()) {
				s.setValidationResponse(new ValidationResponse(
						validationSuccess,
						o.readValue(rs.getString("validationresponse"),new TypeReference<List<ValidationStep>>() {}),
						rs.getString("validationfailmessage")));

			}

			s.setStatus(rs.getString("status"));

			return s;
		} catch (IOException e) {
			throw new SQLException("Failed to deserialise json object",e);
		}
	}
	
	public static Submission getByRepoIdAndTag(String repoId, String tag, QueryRunner q) throws SQLException {
		return q.query("select * from submissions where repoid =? and tag = ?", new ResultSetHandler<Submission>() {
			@Override
			public Submission handle(ResultSet rs) throws SQLException {
				if (rs.next()) {
					return resultSetToSubmission(rs);
				}
				else {
					return null;
				}
			}
			
		},repoId,tag);
	}
	
	public static List<Submission> getPending(QueryRunner q) throws SQLException {
		return q.query("SELECT * from submissions where status = ?",new ResultSetHandler<List<Submission>>() {
			@Override
			public List<Submission> handle(ResultSet rs) throws SQLException {
				List<Submission> result = new LinkedList<>();
				while(rs.next()) {
					result.add(resultSetToSubmission(rs));
				}
				return result;
			}
			
		},Submission.STATUS_PENDING);
	}

}