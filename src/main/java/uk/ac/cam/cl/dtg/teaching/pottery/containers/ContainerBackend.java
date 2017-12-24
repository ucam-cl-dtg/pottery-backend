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

import java.util.function.Function;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;

/** Abstraction for the container backend service. */
public interface ContainerBackend extends Stoppable {

  enum ApiStatus {
    OK,
    UNINITIALISED,
    FAILED,
    SLOW_RESPONSE_TIME
  }

  ApiStatus getApiStatus();

  long getSmoothedCallTime();

  String getVersion() throws ApiUnavailableException;

  void setTimeoutMultiplier(int multiplier);

  <T> ContainerExecResponse<T> executeContainer(
      ExecutionConfig executionConfig, Function<String, T> converter)
      throws ContainerExecutionException, ApiUnavailableException;
}
