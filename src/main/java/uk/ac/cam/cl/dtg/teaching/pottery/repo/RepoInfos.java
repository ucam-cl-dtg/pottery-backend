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

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfo;

import java.sql.SQLException;
import java.sql.Timestamp;

public class RepoInfos {
  public static RepoInfo getByRepoId(String repoId, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT * from repos where repoid=?", new BeanHandler<RepoInfo>(RepoInfo.class), repoId);
  }

  public static void insert(RepoInfo repoInfo, QueryRunner q) throws SQLException {
    q.update(
        "INSERT INTO repos(repoid,taskid,using_testing_version,expiryDate) values (?,?,?,?)",
        repoInfo.getRepoId(),
        repoInfo.getTaskId(),
        repoInfo.isUsingTestingVersion(),
        new Timestamp(repoInfo.getExpiryDate().getTime()));
  }
}
