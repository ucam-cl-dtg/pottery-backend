/*
 * pottery-backend-interface - Backend API for testing programming exercises
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Testcase {

  private final String directory;

  private final String action;

  private final String expectedFailureStep;

  @JsonCreator
  public Testcase(
      @JsonProperty("directory") String directory,
      @JsonProperty("action") String action,
      @JsonProperty("expected") String expectedFailureStep) {
    this.directory = directory;
    this.action = action;
    this.expectedFailureStep = expectedFailureStep;
  }

  public String getDirectory() {
    return directory;
  }

  public String getAction() {
    return action;
  }

  @JsonProperty("expected")
  public String getExpectedFailureStep() {
    return expectedFailureStep;
  }
}
