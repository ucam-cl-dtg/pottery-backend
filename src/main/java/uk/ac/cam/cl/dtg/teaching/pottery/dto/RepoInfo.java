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

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;

public class RepoInfo {

  private String repoId;
  private String taskId;
  private boolean usingTestingVersion;
  private Date expiryDate;

  public RepoInfo() {}

  public RepoInfo(String repoId, String taskId, boolean usingTestingVersion, Date expiryDate) {
    super();
    this.repoId = repoId;
    this.taskId = taskId;
    this.usingTestingVersion = usingTestingVersion;
    this.expiryDate = expiryDate;
  }

  public Date getExpiryDate() {
    return expiryDate;
  }

  public void setExpiryDate(Date expiryDate) {
    this.expiryDate = expiryDate;
  }

  public void setRepoId(String repoId) {
    this.repoId = repoId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getRepoId() {
    return repoId;
  }

  public String getTaskId() {
    return taskId;
  }

  public boolean isUsingTestingVersion() {
    return usingTestingVersion;
  }

  public void setUsingTestingVersion(boolean usingTestingVersion) {
    this.usingTestingVersion = usingTestingVersion;
  }

  public static RepoInfo getByRepoId(String repoId, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT * from repos where repoid=?", new BeanHandler<RepoInfo>(RepoInfo.class), repoId);
  }

  public void insert(QueryRunner q) throws SQLException {
    q.update(
        "INSERT INTO repos(repoid,taskid,using_testing_version,expiryDate) values (?,?,?,?)",
        repoId,
        taskId,
        usingTestingVersion,
        new Timestamp(expiryDate.getTime()));
  }
}
