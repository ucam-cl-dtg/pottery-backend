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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.apache.commons.dbutils.QueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfo;

public class RepoInfos {

  private static ObjectMapper objectMapper = new ObjectMapper();

  /** Look up a repo from the database by its repoId. */
  public static RepoInfo getByRepoId(String repoId, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT repoid,taskid,using_testing_version,expiryDate,variant,remote,mutation,"
            + "parameters from repos where repoid=?",
        rs -> {
          rs.next();
          try {
            return new RepoInfo(
                rs.getString("repoid"),
                rs.getString("taskid"),
                rs.getBoolean("using_testing_version"),
                new Date(rs.getTimestamp("expiryDate").getTime()),
                rs.getString("variant"),
                rs.getString("remote"),
                rs.getInt("mutation"),
                objectMapper.readTree(rs.getString("parameters")));
          } catch (IOException e) {
            throw new SQLException("Couldn't deserialize parameters JSON", e);
          }
        },
        repoId);
  }

  /** Insert this repo in to the database. */
  public static void insert(RepoInfo repoInfo, QueryRunner q) throws SQLException {
    try {
      q.update(
          "INSERT INTO repos(repoid,taskid,using_testing_version,expiryDate,variant,"
              + "remote,mutation,parameters) values (?,?,?,?,?,?,?,?)",
          repoInfo.getRepoId(),
          repoInfo.getTaskId(),
          repoInfo.isUsingTestingVersion(),
          new Timestamp(repoInfo.getExpiryDate().getTime()),
          repoInfo.getVariant(),
          repoInfo.getRemote(),
          repoInfo.getMutation(),
          objectMapper.writeValueAsString(repoInfo.getParameters()));
    } catch (JsonProcessingException e) {
      throw new SQLException("Couldn't serialize parameters JSON", e);
    }
  }
}
