package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.CompilationResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.HarnessStep;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.ValidationResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.java.dto.ValidationStep;

public class Submission {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_COMPLETE = "COMPLETE";
	
	private final Integer submissionId;
	private final String repoId;
	private final String tag;
	private final CompilationResponse compilationResponse;
	private final HarnessResponse harnessResponse;
	private final ValidationResponse validationResponse;	
	private final String status;

	private Submission(Integer submissionId, String repoId, String tag, CompilationResponse compilationResponse,
			HarnessResponse harnessResponse, ValidationResponse validationResponse, String status) {
		super();
		this.submissionId = submissionId;
		this.repoId = repoId;
		this.tag = tag;
		this.compilationResponse = compilationResponse;
		this.harnessResponse = harnessResponse;
		this.validationResponse = validationResponse;
		this.status = status;
	}

	public Integer getSubmissionId() {
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

	public CompilationResponse getCompilationResponse() {
		return compilationResponse;
	}

	public HarnessResponse getHarnessResponse() {
		return harnessResponse;
	}

	public ValidationResponse getValidationResponse() {
		return validationResponse;
	}

	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		
		private String repoId;
		private String tag;
		private int submissionId;
		private CompilationResponse compilationResponse;
		private ValidationResponse validationResponse;
		private HarnessResponse harnessResponse;
		private String status = STATUS_PENDING;
		
		private Builder() {}
		
		public Builder withRepoId(String repoId) {
			this.repoId = repoId;
			return this;
		}
		
		public Builder withTag(String tag) {
			this.tag = tag;
			return this;
		}
		
		public Builder withSubmissionId(int submissionId) {
			this.submissionId = submissionId;
			return this;
		}
		
		public Builder withCompilationResponse(CompilationResponse r) {
			this.compilationResponse = r;
			return this;
		}
		
		public Builder withHarnessResponse(HarnessResponse r) {
			this.harnessResponse = r;
			return this;
		}
		
		public Builder withValidationResponse(ValidationResponse r) {
			this.validationResponse = r;
			return this;
		}
		
		public Builder withStatus(String status) {
			this.status = status;
			return this;
		}
		
		public Submission build() {
			return new Submission(submissionId,repoId,tag,compilationResponse,harnessResponse,validationResponse,status);
		}	
	}
	
	public Submission insert(TransactionQueryRunner q) throws SQLException {
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
					+ "compilationTimeMs,"
					+ "harnesssuccess,"
					+ "harnessresponse,"
					+ "harnessfailmessage,"
					+ "harnessTimeMs,"
					+ "validationsuccess,"
					+ "validationresponse,"
					+ "validationfailmessage,"
					+ "validationTimeMs) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
					s,
					repoId,
					tag,
					status,
					compilationResponse == null ? null : compilationResponse.isSuccess(),
					compilationResponse == null ? null : mapper.writeValueAsString(compilationResponse.getResponse()),
					compilationResponse == null ? null : compilationResponse.getFailMessage(),
					compilationResponse == null ? null :compilationResponse.getExecutionTimeMs(),
					harnessResponse == null ? null : harnessResponse.isSuccess(),
					harnessResponse == null ? null : mapper.writeValueAsString(harnessResponse.getResponse()),
					harnessResponse == null ? null : harnessResponse.getFailMessage(),
					harnessResponse == null ? null : harnessResponse.getExecutionTimeMs(),
					validationResponse == null ? null : validationResponse.isSuccess(),
					validationResponse == null ? null : mapper.writeValueAsString(validationResponse.getResponse()),
					validationResponse == null ? null : validationResponse.getFailMessage(),
					validationResponse == null ? null : validationResponse.getExecutionTimeMs()
					);
		} catch (JsonProcessingException e) {
			throw new SQLException("Failed to serialise object",e);
		}
		q.commit();
		
		return new Submission(s,repoId,tag,compilationResponse,harnessResponse,validationResponse,status);
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
					+ "compilationTimeMs=?"
					+ "harnesssuccess=?,"
					+ "harnessresponse=?,"
					+ "harnessfailmessage=?,"
					+ "harnessTimeMs=?"
					+ "validationsuccess=?,"
					+ "validationresponse=?,"
					+ "validationfailmessage=?"
					+ "validationTimeMs=?"
					+ " where "
					+ "submissionid=?",
					repoId,
					tag,
					status,
					compilationResponse == null ? null :compilationResponse.isSuccess(),
					compilationResponse == null ? null :mapper.writeValueAsString(compilationResponse.getResponse()),
					compilationResponse == null ? null :compilationResponse.getFailMessage(),
					compilationResponse == null ? null :compilationResponse.getExecutionTimeMs(),
					harnessResponse == null ? null : harnessResponse.isSuccess(),
					harnessResponse == null ? null : mapper.writeValueAsString(harnessResponse.getResponse()),
					harnessResponse == null ? null : harnessResponse.getFailMessage(),
					harnessResponse == null ? null : harnessResponse.getExecutionTimeMs(),
					validationResponse == null ? null : validationResponse.isSuccess(),
					validationResponse == null ? null : mapper.writeValueAsString(validationResponse.getResponse()),
					validationResponse == null ? null : validationResponse.getFailMessage(),
					validationResponse == null ? null : validationResponse.getExecutionTimeMs(),					
					submissionId
					);
		} catch (JsonProcessingException e) {
			throw new SQLException("Failed to serialise object",e);
		}
		q.commit();
	}
	
	
	private static Submission resultSetToSubmission(ResultSet rs) throws SQLException {
		try {
			Builder b = builder()
					.withRepoId(rs.getString("repoId"))
					.withTag(rs.getString("tag"))
					.withSubmissionId(rs.getInt("submissionId"));
					
			ObjectMapper o = new ObjectMapper();
			boolean compilationSuccess = rs.getBoolean("compilationSuccess");
			if (!rs.wasNull()) {
				b.withCompilationResponse(new CompilationResponse(
						compilationSuccess,
						rs.getString("compilationfailmessage"),
						rs.getString("compilationresponse"),
						rs.getLong("compilationTimeMs")));
			}

			boolean harnessSuccess = rs.getBoolean("harnessSuccess");
			if (!rs.wasNull()) {
				b.withHarnessResponse(new HarnessResponse(
						harnessSuccess,
						o.readValue(rs.getString("harnessresponse"),new TypeReference<List<HarnessStep>>() {}),
						rs.getString("harnessfailmessage"),
						rs.getLong("harnessTimeMs")));
			}

			boolean validationSuccess = rs.getBoolean("validationSuccess");
			if (!rs.wasNull()) {
				b.withValidationResponse(new ValidationResponse(
						validationSuccess,
						o.readValue(rs.getString("validationresponse"),new TypeReference<List<ValidationStep>>() {}),
						rs.getString("validationfailmessage"),
						rs.getLong("validationTimeMs")));
			}

			b.withStatus(rs.getString("status"));

			return b.build();
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
}