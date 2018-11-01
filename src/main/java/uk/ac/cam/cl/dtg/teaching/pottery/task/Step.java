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
import com.wordnik.swagger.annotations.ApiModelProperty;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Step represents a step that Pottery carries out when processing the user's code.
 *
 * <p>A Step has a map of variant names to Executions for that variant. If no Execution is provided
 * for a variant, then the Execution for the "default" variant is run, if it exists. If it does not
 * exist, no Execution is run for this step.
 *
 * <p>For example, a compile Step might only have Executions specified for variants that require
 * compilation, and a validation Step might only have a default variant so that all variants are
 * validated with the same code.
 */
public class Step {
  @ApiModelProperty("Map from variants to execution to run for this step.")
  private Map<String, Execution> execution;

  @JsonCreator
  public Step(@JsonProperty("execution") Map<String, Execution> execution) {
    this.execution = execution;
  }

  @JsonProperty("execution")
  public Map<String, Execution> getExecutionMap() {
    return execution;
  }

  public Step withDefaultContainerRestriction(ContainerRestrictions restrictions) {
    return new Step(
        execution
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    e -> e.getKey(),
                    e -> e.getValue().withDefaultContainerRestriction(restrictions))));
  }
}
