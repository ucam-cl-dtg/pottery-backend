/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015 Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessPart;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Interpretation;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.ValidatorResponse;

public class Submission {

  public static final String STATUS_PENDING = "PENDING";

  public static final String STATUS_COMPILATION_RUNNING = "COMPILATION_RUNNING";
  public static final String STATUS_COMPILATION_FAILED = "COMPILATION_FAILED";
  public static final String STATUS_COMPILATION_COMPLETE = "COMPILATION_COMPLETE";

  public static final String STATUS_HARNESS_RUNNING = "HARNESS_RUNNING";
  public static final String STATUS_HARNESS_FAILED = "HARNESS_FAILED";
  public static final String STATUS_HARNESS_COMPLETE = "HARNESS_COMPLETE";

  public static final String STATUS_VALIDATOR_RUNNING = "VALIDATOR_RUNNING";
  public static final String STATUS_VALIDATOR_FAILED = "VALIDATOR_FAILED";
  public static final String STATUS_VALIDATOR_COMPLETE = "VALIDATOR_COMPLETE";

  public static final String STATUS_COMPLETE = "COMPLETE";

  private final String repoId;
  private final String tag;

  private String compilationOutput;

  private long compilationTimeMs;
  private long harnessTimeMs;
  private long validatorTimeMs;

  private long waitTimeMs;
  private Date dateScheduled;

  private List<TestStep> testSteps;

  private String errorMessage;

  private String status;

  private boolean needsRetry;

  public static final String INTERPRETATION_BAD = Interpretation.INTERPRETED_FAILED;
  public static final String INTERPRETATION_ACCEPTABLE = Interpretation.INTERPRETED_ACCEPTABLE;
  public static final String INTERPRETATION_EXCELLENT = Interpretation.INTERPRETED_PASSED;

  private String interpretation;

  public Submission(
      String repoId,
      String tag,
      String compilationOutput,
      long compilationTimeMs,
      long harnessTimeMs,
      long validatorTimeMs,
      long waitTimeMs,
      List<TestStep> testSteps,
      String errorMessage,
      String status,
      Date dateScheduled,
      String interpretation,
      boolean needsRetry) {
    super();
    this.repoId = repoId;
    this.tag = tag;
    this.compilationOutput = compilationOutput;
    this.compilationTimeMs = compilationTimeMs;
    this.harnessTimeMs = harnessTimeMs;
    this.validatorTimeMs = validatorTimeMs;
    this.waitTimeMs = waitTimeMs;
    this.testSteps = testSteps;
    this.errorMessage = errorMessage;
    this.status = status;
    this.dateScheduled = dateScheduled;
    this.interpretation = interpretation;
    this.needsRetry = needsRetry;
  }

  public boolean isNeedsRetry() {
    return needsRetry;
  }

  public String getInterpretation() {
    return interpretation;
  }

  public Date getDateScheduled() {
    return dateScheduled;
  }

  public String getRepoId() {
    return repoId;
  }

  public String getTag() {
    return tag;
  }

  public String getCompilationOutput() {
    return compilationOutput;
  }

  public long getCompilationTimeMs() {
    return compilationTimeMs;
  }

  public long getHarnessTimeMs() {
    return harnessTimeMs;
  }

  public long getValidatorTimeMs() {
    return validatorTimeMs;
  }

  public long getWaitTimeMs() {
    return waitTimeMs;
  }

  public List<TestStep> getTestSteps() {
    return testSteps;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getStatus() {
    return status;
  }

  public static Builder builder(String repoId, String tag) {
    return new Builder(repoId, tag);
  }

  public static class Builder {

    private final Date dateScheduled;
    private final String repoId;
    private final String tag;
    private String compilationOutput;
    private long compilationTimeMs = -1;
    private long harnessTimeMs = -1;
    private long validatorTimeMs = -1;
    private long waitTimeMs;
    private List<HarnessPart> harnessParts;
    private List<Interpretation> validatorInterpretations;
    private String errorMessage;
    private String status;
    private String interpretation;
    private boolean needsRetry;

    private Builder(String repoId, String tag) {
      this.repoId = repoId;
      this.tag = tag;
      this.status = STATUS_PENDING;
      this.dateScheduled = new Date();
    }

    public Builder setStatus(String status) {
      this.status = status;
      return this;
    }

    public Builder setRetry() {
      this.needsRetry = true;
      return this;
    }

    public Builder addErrorMessage(String m) {
      if (m != null) {
        if (this.errorMessage == null) {
          this.errorMessage = m;
        } else {
          this.errorMessage += " " + m;
        }
      }
      return this;
    }

    public Builder setCompilationResponse(
        String compilationOutput, boolean success, long executionTimeMs) {
      this.status = success ? STATUS_COMPILATION_COMPLETE : STATUS_COMPILATION_FAILED;
      this.compilationOutput = compilationOutput;
      this.compilationTimeMs = executionTimeMs;
      return this;
    }

    /**
     * Update the builder with harness response. Note that this only takes a shallow copy of its
     * arguments
     *
     * @param h
     * @param success
     * @param executionTimeMs
     * @return
     */
    public Builder setHarnessResponse(HarnessResponse h, long executionTimeMs) {
      this.status = h.isCompleted() ? STATUS_HARNESS_COMPLETE : STATUS_HARNESS_FAILED;
      this.harnessParts = h.getTestParts();
      this.harnessTimeMs = executionTimeMs;
      return this;
    }

    public Builder setValidatorResponse(ValidatorResponse v, long executionTimeMs) {
      this.status = v.isCompleted() ? STATUS_VALIDATOR_COMPLETE : STATUS_VALIDATOR_FAILED;
      this.validatorInterpretations = v.getInterpretations();
      this.validatorTimeMs = executionTimeMs;
      return this;
    }

    public Builder setStarted() {
      this.waitTimeMs = System.currentTimeMillis() - dateScheduled.getTime();
      this.errorMessage = null;
      this.compilationOutput = null;
      this.validatorInterpretations = null;
      this.harnessParts = null;
      this.compilationTimeMs = -1;
      this.validatorTimeMs = -1;
      this.harnessTimeMs = -1;
      this.interpretation = null;
      return this;
    }

    public Submission build() {
      Map<String, Interpretation> i =
          validatorInterpretations == null
              ? null
              : validatorInterpretations
                  .stream()
                  .collect(Collectors.toMap(Interpretation::getId, Function.identity()));
      List<TestStep> testSteps =
          harnessParts == null
              ? null
              : harnessParts.stream().map(p -> new TestStep(p, i)).collect(Collectors.toList());

      return new Submission(
          repoId,
          tag,
          compilationOutput,
          compilationTimeMs,
          harnessTimeMs,
          validatorTimeMs,
          waitTimeMs,
          testSteps,
          errorMessage,
          status,
          dateScheduled,
          interpretation,
          needsRetry);
    }

    public Builder setInterpretation(String interpretation) {
      this.interpretation = interpretation;
      return this;
    }

    public void setComplete() {

      if (errorMessage != null) {
        interpretation = INTERPRETATION_BAD;
      }

      if (STATUS_PENDING.equals(status) || STATUS_COMPILATION_RUNNING.equals(status)) {
        status = STATUS_COMPILATION_FAILED;
        interpretation = INTERPRETATION_BAD;
        return;
      }

      if (STATUS_COMPILATION_COMPLETE.equals(status) || STATUS_HARNESS_RUNNING.equals(status)) {
        status = STATUS_HARNESS_FAILED;
        interpretation = INTERPRETATION_BAD;
        return;
      }

      if (STATUS_HARNESS_COMPLETE.equals(status) || STATUS_VALIDATOR_RUNNING.equals(status)) {
        status = STATUS_VALIDATOR_FAILED;
        interpretation = INTERPRETATION_BAD;
        return;
      }

      status = STATUS_COMPLETE;
    }
  }

  public void insert(TransactionQueryRunner q) throws SQLException {
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
          repoId,
          tag,
          status,
          compilationOutput,
          compilationTimeMs,
          harnessTimeMs,
          validatorTimeMs,
          waitTimeMs,
          errorMessage,
          testSteps == null ? null : mapper.writeValueAsString(testSteps),
          new Timestamp(dateScheduled.getTime()),
          interpretation);
    } catch (JsonProcessingException e) {
      throw new SQLException("Failed to serialise object", e);
    }
    q.commit();
  }

  public void update(TransactionQueryRunner q) throws SQLException {
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
          status,
          compilationOutput,
          compilationTimeMs,
          harnessTimeMs,
          validatorTimeMs,
          waitTimeMs,
          errorMessage,
          testSteps == null ? null : mapper.writeValueAsString(testSteps),
          new Timestamp(dateScheduled.getTime()),
          interpretation,
          repoId,
          tag);
    } catch (JsonProcessingException e) {
      throw new SQLException("Failed to serialise object", e);
    }
    q.commit();
  }

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
}
