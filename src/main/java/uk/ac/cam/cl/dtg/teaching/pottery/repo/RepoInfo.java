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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import javax.annotation.Nullable;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfoWithStatus;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoStatus;

public class RepoInfo {

  public static final String REMOTE_UNSET = "";

  private String repoId;
  private String taskId;
  private boolean usingTestingVersion;
  private Date expiryDate;

  private String variant;

  /** If this value is set then indicates that this repo is hosted remotely. */
  private String remote;

  /** If this value is set then indicates that this repo had an error when being created. */
  private String errorMessage;

  /** The identifier of the mutation for this repo, or -1 if this repo has no mutations. */
  private int mutationId;

  /** The customised problemStatement for this repo. */
  @Nullable
  private String problemStatement;

  @JsonCreator
  public RepoInfo(
      @JsonProperty("repoId") String repoId,
      @JsonProperty("taskId") String taskId,
      @JsonProperty("usingTestingVersion") boolean usingTestingVersion,
      @JsonProperty("expiryDate") Date expiryDate,
      @JsonProperty("variant") String variant,
      @JsonProperty("remote") String remote,
      @JsonProperty("errorMessage") String errorMessage,
      @JsonProperty("mutationId") int mutationId,
      @JsonProperty("problemStatement") String problemStatement
      ) {
    super();
    this.repoId = repoId;
    this.taskId = taskId;
    this.usingTestingVersion = usingTestingVersion;
    this.expiryDate = expiryDate;
    this.variant = variant;
    this.remote = remote;
    this.errorMessage = errorMessage;
    this.mutationId = mutationId;
    this.problemStatement = problemStatement;
  }

  public Date getExpiryDate() {
    return expiryDate;
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

  public String getVariant() {
    return variant;
  }

  public String getRemote() {
    return remote;
  }

  public boolean isRemote() {
    return !remote.equals(REMOTE_UNSET);
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public int getMutationId() {
    return mutationId;
  }

  @Nullable
  public String getProblemStatement() {
    return problemStatement;
  }

  public RepoInfo withExpiryDate(Date newExpiryDate) {
    return new RepoInfo(repoId, taskId, usingTestingVersion, newExpiryDate, variant, remote,
        errorMessage, mutationId, problemStatement);
  }

  public RepoInfo withError(String message) {
    return new RepoInfo(repoId, taskId, usingTestingVersion, expiryDate, variant, remote, message,
        mutationId, problemStatement);
  }

  public RepoInfo withProblemStatement(String newProblemStatement) {
    return new RepoInfo(repoId, taskId, usingTestingVersion, expiryDate, variant, remote,
        errorMessage, mutationId, newProblemStatement);
  }

  public RepoInfoWithStatus withStatus(boolean ready) {
    boolean expired = this.getExpiryDate() != null
        && new Date().after(this.getExpiryDate());
    RepoStatus status = errorMessage != null ? RepoStatus.ERROR :
        ready ? (expired ? RepoStatus.EXPIRED : RepoStatus.READY)
        : RepoStatus.CREATING;
    return new RepoInfoWithStatus(repoId, taskId, usingTestingVersion, status, expiryDate, variant,
        remote, errorMessage, problemStatement);
  }
}
