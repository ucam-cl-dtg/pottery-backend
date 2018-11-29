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

/** A ParameterisationResult represents the result from running a parameterisation script. */
public class ParameterisationResult {
  @ApiModelProperty("The problem statement as an HTML fragment.")
  private final String problemStatement;

  @ApiModelProperty("The names of any files created as the initial skeleton.")
  private final List<String> files;

  @JsonCreator
  public ParameterisationResult(@JsonProperty("problemStatement") String problemStatement,
                                @JsonProperty("files") List<String> files) {
    this.problemStatement = problemStatement;
    this.files = files;
  }

  public String getProblemStatement() {
    return problemStatement;
  }

  public List<String> getFiles() {
    return files;
  }
}

