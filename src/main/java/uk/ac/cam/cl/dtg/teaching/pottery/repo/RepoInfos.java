/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
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

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.apache.commons.dbutils.QueryRunner;

public class RepoInfos {

  /** Look up a repo from the database by its repoId. */
  public static RepoInfo getByRepoId(String repoId, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT repoid,taskid,using_testing_version,expiryDate,variant,remote,errormessage,"
            + " mutationid,problemstatement from repos where repoid=?",
        rs -> {
          if (!rs.next()) {
            throw new SQLException("Repo with ID " + repoId + " not found in database.");
          }
          Timestamp expiryDate = rs.getTimestamp("expiryDate");
          return new RepoInfo(
              rs.getString("repoid"),
              rs.getString("taskid"),
              rs.getBoolean("using_testing_version"),
              expiryDate != null ? new Date(expiryDate.getTime()) : null,
              rs.getString("variant"),
              rs.getString("remote"),
              rs.getString("errormessage"),
              rs.getInt("mutationid"),
              rs.getString("problemstatement"));
        },
        repoId);
  }

  /** Insert this repo in to the database. */
  public static void insert(RepoInfo repoInfo, QueryRunner q) throws SQLException {
    q.update(
        "INSERT INTO repos(repoid,taskid,using_testing_version,expiryDate,variant,"
            + "remote, errormessage, mutationid, problemstatement) values (?,?,?,?,?,?,?,?,?)",
        repoInfo.getRepoId(),
        repoInfo.getTaskId(),
        repoInfo.isUsingTestingVersion(),
        repoInfo.getExpiryDate() != null ? new Timestamp(repoInfo.getExpiryDate().getTime()) : null,
        repoInfo.getVariant(),
        repoInfo.getRemote(),
        repoInfo.getErrorMessage(),
        repoInfo.getMutationId(),
        repoInfo.getProblemStatement());
  }

  /** Update this repo in the database. */
  public static void update(RepoInfo repoInfo, QueryRunner q) throws SQLException {
    q.update(
        "UPDATE repos SET taskid = ?, using_testing_version = ?, expiryDate = ?, variant = ?,"
            + "remote = ?, errormessage = ?, mutationid = ?, problemstatement = ? WHERE repoid = ?",
        repoInfo.getTaskId(),
        repoInfo.isUsingTestingVersion(),
        repoInfo.getExpiryDate() != null ? new Timestamp(repoInfo.getExpiryDate().getTime()) : null,
        repoInfo.getVariant(),
        repoInfo.getRemote(),
        repoInfo.getErrorMessage(),
        repoInfo.getMutationId(),
        repoInfo.getProblemStatement(),
        repoInfo.getRepoId());
  }
}
