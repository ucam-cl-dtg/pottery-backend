/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 Andrew Rice (acr31@cam.ac.uk), BlueOptima Limited
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

package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.dbutils.QueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.model.StepResult;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;

public class Submissions {
  private static Submission resultSetToSubmission(ResultSet rs, QueryRunner q) throws SQLException {
    final String repoId = rs.getString("repoId");
    final String tag = rs.getString("tag");

    List<StepResult> steps = q.query("select * from outputs where repoid = ? and tag = ?"
            + " order by position",
        srs -> {
          List<StepResult> stepResults = new ArrayList<>();
          while (srs.next()) {
            stepResults.add(new StepResult(
                srs.getString("step"),
                srs.getString("status"),
                srs.getLong("timems")
            ));
          }
          return stepResults;
        }, repoId, tag);
    return new Submission(
        repoId,
        tag,
        rs.getString("status"),
        steps,
        rs.getString("errormessage"),
        rs.getTimestamp("dateScheduled"),
        false);
  }

  /** Lookup a Submission by its repoId and the tag (sha hash). */
  public static Submission getByRepoIdAndTag(String repoId, String tag, QueryRunner q)
      throws SQLException {
    return q.query(
        "select * from submissions where repoid =? and tag = ?",
        rs -> rs.next() ? resultSetToSubmission(rs, q) : null,
        repoId,
        tag);
  }

  /** Insert this submission into the database. */
  public static void insert(Submission submission, TransactionQueryRunner q) throws SQLException {
    q.update(
        "INSERT into submissions ("
            + "repoid,"
            + "tag,"
            + "status,"
            + "errormessage,"
            + "dateScheduled"
            + ") VALUES (?,?,?,?,?)",
        submission.getRepoId(),
        submission.getTag(),
        submission.getStatus(),
        submission.getErrorMessage(),
        new Timestamp(submission.getDateScheduled().getTime()));
    for (int i = 0; i < submission.getSteps().size(); i++) {
      StepResult step = submission.getSteps().get(i);
      q.update("INSERT into outputs ("
              + "repoid,"
              + "tag,"
              + "position,"
              + "step,"
              + "status,"
              + "timems,"
              + "output"
              + ") VALUES (?,?,?,?,?,?,?)",
          submission.getRepoId(),
          submission.getTag(),
          i,
          step.getName(),
          step.getStatus(),
          step.getMsec(),
          step.getOutput());
    }
    q.commit();
  }

  public static void delete(Submission submission, TransactionQueryRunner q) throws SQLException {
    q.update(
        "DELETE from submissions where repoId=? and tag=?",
        submission.getRepoId(),
        submission.getTag());
    q.update(
        "DELETE from outputs where repoId=? and tag=?",
        submission.getRepoId(),
        submission.getTag());
  }

  public static String getOutputByRepoIdAndTagAndStep(String repoId, String tag, String step,
                                                      TransactionQueryRunner q)
      throws SQLException {
    return q.query(
        "select output from outputs where repoid =? and tag = ? and step = ?"
            + " ORDER BY position LIMIT 1",
        rs -> rs.next() ? rs.getString("output") : null,
        repoId,
        tag,
        step);
  }
}
