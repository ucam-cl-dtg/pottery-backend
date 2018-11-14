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

/** A Parameterisation represents the instructions for parameterising a task. */
public class Parameterisation {
  @ApiModelProperty("Number of parameterisations available.")
  private final int count;

  @ApiModelProperty("Step to generate the parameterised task problem statement.")
  private final Step generator;

  @JsonCreator
  public Parameterisation(
      @JsonProperty("count") int count, @JsonProperty("generator") Step generator) {
    this.count = count;
    this.generator = generator;
  }

  public int getCount() {
    return count;
  }

  public Step getGenerator() {
    return generator;
  }

  public Parameterisation withDefaultContainerRestrictions(ContainerRestrictions restrictions) {
    return new Parameterisation(getCount(),
        getGenerator().withDefaultContainerRestriction(restrictions));
  }
}

