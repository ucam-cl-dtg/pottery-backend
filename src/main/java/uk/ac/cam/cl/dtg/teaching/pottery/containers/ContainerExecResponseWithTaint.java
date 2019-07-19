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
import uk.ac.cam.cl.dtg.teaching.pottery.containers.taint.Taint;

@AutoValue
public abstract class ContainerExecResponseWithTaint {

  public abstract ContainerExecResponse.Status status();

  public abstract String response();

  public abstract long executionTimeMs();
  public abstract Taint taint();

  public static ContainerExecResponseWithTaint create(ContainerExecResponse response, Taint taint) {
    return new AutoValue_ContainerExecResponseWithTaint(response.status(), response.response(),
        response.executionTimeMs(), taint);
  }
}
