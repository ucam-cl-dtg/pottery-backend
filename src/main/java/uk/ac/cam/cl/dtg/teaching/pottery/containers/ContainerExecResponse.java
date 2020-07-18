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
package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.taint.Taint;

@AutoValue
public abstract class ContainerExecResponse {

  public enum Status {
    COMPLETED,
    FAILED_EXITCODE,
    FAILED_OOM,
    FAILED_DISK,
    FAILED_TIMEOUT,
    FAILED_UNKNOWN,
    FAILED_OUTPUT
  }

  public abstract Status status();

  public abstract String response();

  public abstract long executionTimeMs();

  @Nullable
  public abstract Taint taint();

  public abstract String containerName();

  public static ContainerExecResponse create(
      Status status, String response, long executionTimeMs, Taint taint, String containerName) {
    return new AutoValue_ContainerExecResponse(
        status, stripNull(response), executionTimeMs, taint, containerName);
  }

  private static String stripNull(String v) {
    if (v == null) {
      return v;
    }
    return v.replace('\0', '?');
  }
}
