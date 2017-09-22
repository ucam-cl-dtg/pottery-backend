package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TestStep;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class Submissions {
    private static Submission resultSetToSubmission(ResultSet rs) throws SQLException {
      try {
        ObjectMapper o = new ObjectMapper();
        String testPartsString = rs.getString("testSteps");
        List<TestStep> testSteps = null;
        if (!rs.wasNull()) {
          testSteps = o.readValue(testPartsString, new TypeReference<List<TestStep>>() {});
        }

        return new Submission(
            rs.getString("repoId"),
            rs.getString("tag"),
            rs.getString("compilationOutput"),
            rs.getLong("compilationTimeMs"),
            rs.getLong("harnessTimeMs"),
            rs.getLong("validatorTimeMs"),
            rs.getLong("waitTimeMs"),
            testSteps,
            rs.getString("errorMessage"),
            rs.getString("status"),
            rs.getTimestamp("dateScheduled"),
            rs.getString("interpretation"),
            false);
      } catch (IOException e) {
        throw new SQLException("Failed to deserialise json object " + e.getMessage(), e);
      }
    }

    public static Submission getByRepoIdAndTag(String repoId, String tag, QueryRunner q)
        throws SQLException {
      return q.query(
          "select * from submissions where repoid =? and tag = ?",
          new ResultSetHandler<Submission>() {
            @Override
            public Submission handle(ResultSet rs) throws SQLException {
              if (rs.next()) {
                return resultSetToSubmission(rs);
              } else {
                return null;
              }
            }
          },
          repoId,
          tag);
    }

    public static void insert(Submission submission, TransactionQueryRunner q) throws SQLException {
      ObjectMapper mapper = new ObjectMapper();

      try {
        q.update(
            "INSERT into submissions ("
                + "repoid,"
                + "tag,"
                + "status,"
                + "compilationoutput,"
                + "compilationTimeMs,"
                + "harnessTimeMs,"
                + "validatorTimeMs,"
                + "waitTimeMs,"
                + "errorMessage,"
                + "testSteps,"
                + "dateScheduled,"
                + "interpretation"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            submission.getRepoId(),
            submission.getTag(),
            submission.getStatus(),
            submission.getCompilationOutput(),
            submission.getCompilationTimeMs(),
            submission.getHarnessTimeMs(),
            submission.getValidatorTimeMs(),
            submission.getWaitTimeMs(),
            submission.getErrorMessage(),
            submission.getTestSteps() == null
                ? null
                : mapper.writeValueAsString(submission.getTestSteps()),
            new Timestamp(submission.getDateScheduled().getTime()),
            submission.getInterpretation());
      } catch (JsonProcessingException e) {
        throw new SQLException("Failed to serialise object", e);
      }
      q.commit();
    }

    public static void update(Submission submission, TransactionQueryRunner q) throws SQLException {
      ObjectMapper mapper = new ObjectMapper();
      try {
        q.update(
            "update submissions set "
                + "status=?,"
                + "compilationoutput=?,"
                + "compilationTimeMs=?,"
                + "harnessTimeMs=?,"
                + "validatorTimeMs=?"
                + "waitTimeMs=?,"
                + "errorMessage=?,"
                + "testSteps=?,"
                + "dateScheduled=?,"
                + "interpretation=?"
                + " where "
                + "repoId=? and tag=?",
            submission.getStatus(),
            submission.getCompilationOutput(),
            submission.getCompilationTimeMs(),
            submission.getHarnessTimeMs(),
            submission.getValidatorTimeMs(),
            submission.getWaitTimeMs(),
            submission.getErrorMessage(),
            submission.getTestSteps() == null
                ? null
                : mapper.writeValueAsString(submission.getTestSteps()),
            new Timestamp(submission.getDateScheduled().getTime()),
            submission.getInterpretation(),
            submission.getRepoId(),
            submission.getTag());
      } catch (JsonProcessingException e) {
        throw new SQLException("Failed to serialise object", e);
      }
      q.commit();
    }
}
