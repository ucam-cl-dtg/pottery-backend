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

package uk.ac.cam.cl.dtg.teaching.pottery.containers;

public class ContainerExecResponse<T> {

  private boolean success;
  private T response;
  private String rawResponse;
  private long executionTimeMs;

  public ContainerExecResponse() {}

  public ContainerExecResponse(
      boolean success, T response, String rawResponse, long executionTimeMs) {
    super();
    this.success = success;
    this.response = response;
    this.rawResponse = rawResponse;
    this.executionTimeMs = executionTimeMs;
  }

  public long getExecutionTimeMs() {
    return executionTimeMs;
  }

  public void setExecutionTimeMs(long executionTimeMs) {
    this.executionTimeMs = executionTimeMs;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public T getResponse() {
    return response;
  }

  public void setResponse(T response) {
    this.response = response;
  }

  public String getRawResponse() {
    return rawResponse;
  }

  @Override
  public String toString() {
    return "ContainerExecResponse{"
        + "success="
        + success
        + ", response="
        + response
        + ", rawResponse='"
        + rawResponse
        + '\''
        + ", executionTimeMs="
        + executionTimeMs
        + '}';
  }
}
