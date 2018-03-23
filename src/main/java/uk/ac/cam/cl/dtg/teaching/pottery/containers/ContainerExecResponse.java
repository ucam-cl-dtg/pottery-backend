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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ContainerExecResponse<T> {

  public enum Status {
    COMPLETED,
    FAILED_OOM,
    FAILED_DISK,
    FAILED_TIMEOUT,
    FAILED_UNKNOWN
  }

  public abstract Status status();

  public abstract T response();

  public abstract String rawResponse();

  public abstract long executionTimeMs();

  public static <T> ContainerExecResponse<T> create(
      Status status, T response, String rawResponse, long executionTimeMs) {
    return new AutoValue_ContainerExecResponse<>(status, response, rawResponse, executionTimeMs);
  }
}
