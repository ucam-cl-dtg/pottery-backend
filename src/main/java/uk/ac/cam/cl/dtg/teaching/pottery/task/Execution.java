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

/**
 * An Execution represents a program that can be run by Pottery under a particular image.
 *
 * <p>The program command-line can contain variables delimited with @'s (like @VARIABLE@) which are
 * replaced by Pottery according to the context the Execution is run in.
 *
 * <p>The container that is started has restrictions applied to it as specified; if none are
 * specified, Pottery can apply default restrictions.
 */
public class Execution {

  // This is just a marker value and is not expected to actually be used.
  private static final ContainerRestrictions DEFAULT_RESTRICTIONS =
      new ContainerRestrictions(0, 0, 0, true);

  @ApiModelProperty("Image that this execution should be run in.")
  private String image;

  @ApiModelProperty("Command-line for this execution.")
  private String program;

  @ApiModelProperty("Container restrictions on this execution.")
  private ContainerRestrictions restrictions;

  @JsonCreator
  public Execution(
      @JsonProperty("image") String image,
      @JsonProperty("program") String program,
      @JsonProperty("restrictions") ContainerRestrictions restrictions) {
    this.image = image;
    this.program = program;
    this.restrictions = restrictions != null ? restrictions : DEFAULT_RESTRICTIONS;
  }

  public String getImage() {
    return image;
  }

  public String getProgram() {
    return program;
  }

  public ContainerRestrictions getRestrictions() {
    return restrictions;
  }

  public Execution withDefaultContainerRestriction(ContainerRestrictions defaultRestrictions) {
    if (restrictions == DEFAULT_RESTRICTIONS) {
      return new Execution(image, program, defaultRestrictions);
    }
    return this;
  }

  public Execution withProgram(String program) {
    return new Execution(image, program, restrictions);
  }
}
