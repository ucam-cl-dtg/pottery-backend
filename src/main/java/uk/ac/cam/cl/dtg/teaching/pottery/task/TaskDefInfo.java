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
package uk.ac.cam.cl.dtg.teaching.pottery.task;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import org.apache.commons.dbutils.QueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;

@AutoValue
abstract class TaskDefInfo {

  static final String UNSET = "";

  abstract String taskId();

  abstract String registeredTag();

  abstract String testingCopyId();

  abstract String registeredCopyId();

  abstract boolean retired();

  /**
   * If this is non-empty then read the task definition from here rather than using local storage.
   */
  abstract String remote();

  static Builder builder() {
    return new AutoValue_TaskDefInfo.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setTaskId(String taskId);

    abstract Builder setRegisteredTag(String registeredTag);

    abstract Builder setTestingCopyId(String testingCopyId);

    abstract Builder setRegisteredCopyId(String registeredCopyId);

    abstract Builder setRetired(boolean retired);

    abstract Builder setRemote(String remote);

    abstract TaskDefInfo build();
  }

  public static boolean isUnset(String value) {
    return value == null || value.equals(UNSET);
  }

  /** Lookup the TaskDefInfo object for this taskId. */
  public static TaskDefInfo getByTaskId(String taskId, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT "
            + "registeredtag, "
            + "testingCopyId, "
            + "registeredCopyId, "
            + "retired, "
            + "remote from tasks where taskid=?",
        rs -> {
          if (rs.next()) {
            String registeredTag = rs.getString(1);
            String testingCopyId = rs.getString(2);
            String registeredCopyId = rs.getString(3);
            boolean retired = rs.getBoolean(4);
            String remote = rs.getString(5);
            return TaskDefInfo.builder()
                .setTaskId(taskId)
                .setRegisteredTag(registeredTag == null ? UNSET : registeredTag)
                .setTestingCopyId(testingCopyId == null ? UNSET : testingCopyId)
                .setRegisteredCopyId(registeredCopyId == null ? UNSET : registeredCopyId)
                .setRetired(retired)
                .setRemote(remote == null ? UNSET : remote)
                .build();
          }
          return null;
        },
        taskId);
  }

  /** Return a list of all the taskIds which exist in the database. */
  public static ImmutableList<String> getAllTaskIds(QueryRunner q) throws SQLException {
    return q.query(
        "Select taskId from tasks",
        rs -> {
          ImmutableList.Builder<String> builder = ImmutableList.builder();
          while (rs.next()) {
            builder.add(rs.getString(1));
          }
          return builder.build();
        });
  }

  /** Update the sha hash for the registered copy for this task. */
  public static void updateRegisteredCopy(String taskId, String tag, String copyId, QueryRunner q)
      throws SQLException {
    q.update(
        "UPDATE tasks set registeredtag=?,registeredCopyId=? where taskid = ?",
        tag,
        copyId,
        taskId);
  }

  public static void updateTestingCopy(String taskId, String copyId, QueryRunner q)
      throws SQLException {
    q.update("UPDATE tasks set testingCopyId=? where taskid = ?", copyId, taskId);
  }

  public static void updateRetired(String taskId, boolean retired, QueryRunner q)
      throws SQLException {
    q.update("UPDATE tasks set retired=? where taskid = ?", retired, taskId);
  }

  public static boolean isRetired(String taskId, QueryRunner q) throws SQLException {
    return q.query(
        "SELECT retired from tasks where taskId =?",
        rs -> {
          if (!rs.next()) {
            throw new SQLException("Failed to find task " + taskId);
          }
          return rs.getBoolean(1);
        },
        taskId);
  }

  /**
   * Lookup the location of the task definition.This might be a local directory or a remote
   * repository.
   */
  public URI getTaskDefLocation(TaskConfig taskConfig) throws URISyntaxException {
    if (remote().equals(UNSET)) {
      return new URI(
          String.format("file://%s", taskConfig.getLocalTaskDefinitionDir(taskId()).getPath()));
    } else {
      return new URI(remote());
    }
  }

  public void insert(QueryRunner q) throws SQLException {
    q.update(
        "INSERT INTO tasks(taskid,registeredtag,retired,remote) values (?,?,?,?)",
        taskId(),
        registeredTag(),
        retired(),
        remote());
  }
}
