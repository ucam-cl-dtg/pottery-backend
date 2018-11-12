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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;
import java.util.List;

/** An Action represents a set of steps, for example validating a user's submission. */
public class Action {
  @ApiModelProperty("Description of this action.")
  private final String description;

  @ApiModelProperty("List of steps to run for this action.")
  private final List<String> steps;

  @JsonCreator
  public Action(
      @JsonProperty("description") String description, @JsonProperty("steps") List<String> steps) {
    this.description = description;
    this.steps = steps;
  }

  public String getDescription() {
    return description;
  }

  public List<String> getSteps() {
    return steps;
  }
}
