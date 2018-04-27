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

package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.apache.commons.dbutils.QueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;

public class Submissions {
  private static Submission resultSetToSubmission(ResultSet rs) throws SQLException {
    return new Submission(
        rs.getString("repoId"),
        rs.getString("tag"),
        rs.getString("output"),
        rs.getLong("waitTimeMs"),
        rs.getString("errormessage"),
        rs.getString("status"),
        rs.getTimestamp("dateScheduled"),
        false);
  }

  /** Lookup a Submission by its repoId and the tag (sha hash). */
  public static Submission getByRepoIdAndTag(String repoId, String tag, QueryRunner q)
      throws SQLException {
    return q.query(
        "select * from submissions where repoid =? and tag = ?",
        rs -> rs.next() ? resultSetToSubmission(rs) : null,
        repoId,
        tag);
  }

  /** Insert this submission into the database. */
  public static void insert(Submission submission, TransactionQueryRunner q) throws SQLException {
    ObjectMapper mapper = new ObjectMapper();

    q.update(
        "INSERT into submissions ("
            + "repoid,"
            + "tag,"
            + "status,"
            + "output,"
            + "errormessage,"
            + "waitTimeMs,"
            + "dateScheduled"
            + ") VALUES (?,?,?,?,?,?,?)",
        submission.getRepoId(),
        submission.getTag(),
        submission.getStatus(),
        submission.getOutput(),
        submission.getErrorMessage(),
        submission.getWaitTimeMs(),
        new Timestamp(submission.getDateScheduled().getTime()));
    q.commit();
  }

  /** Update this submission in the database. */
  public static void update(Submission submission, TransactionQueryRunner q) throws SQLException {
    ObjectMapper mapper = new ObjectMapper();
    q.update(
        "update submissions set "
            + "status=?,"
            + "output=?,"
            + "errormessage=?,"
            + "waitTimeMs=?,"
            + "dateScheduled=?"
            + " where "
            + "repoId=? and tag=?",
        submission.getStatus(),
        submission.getOutput(),
        submission.getErrorMessage(),
        submission.getWaitTimeMs(),
        submission.getDateScheduled(),
        submission.getRepoId(),
        submission.getTag());
    q.commit();
  }

  public static void delete(Submission submission, TransactionQueryRunner q) throws SQLException {
    q.update(
        "DELETE from submissions where repoId=? and tag=?",
        submission.getRepoId(),
        submission.getTag());
  }
}
