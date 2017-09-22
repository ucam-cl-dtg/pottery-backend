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

package uk.ac.cam.cl.dtg.teaching.pottery.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.annotations.ApiModelProperty;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import uk.ac.cam.cl.dtg.teaching.pottery.Criterion;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerRestrictions;

public class TaskInfo {

  /**
   * Algorithms & Data Structures: Tests the ability of the developer to compose algorithms and
   * design appropriate data structures to solve the problem set out in the test.
   */
  public static final String TYPE_ALGORITHM = "ALGORITHMS";

  /**
   * Design Approaches: Evaluates the logical flow of solution code and appropriate design features
   * of the solution (e.g. ???)
   */
  public static final String TYPE_DESIGN = "DESIGN";

  /**
   * Black Box Testing: Tests the developer's ability to test a solution without knowing or being
   * able to review the underlying source code.
   */
  public static final String TYPE_BLACKBOX = "BLACKBOX";

  /**
   * Unit Testing: Assesses the ability of the developer to write unit tests on pre-existing source
   * code and or source code that they have themselves written.
   */
  public static final String TYPE_UNITTEST = "UNITTEST";

  /**
   * I/O Management: Evaluates the developers ability to implement strategies that result in
   * appropriate I/O activity in a test solution.
   */
  public static final String TYPE_IO = "IO";

  /**
   * Cache & Memory Management: Evaluates the developers ability to implement strategies that result
   * in appropriate cache and memory usage approaches in a test solution.
   */
  public static final String TYPE_MEMORY = "MEMORY";

  /**
   * Using Existing APIs & Libraries: Test the ability of a developer to appropriately exploit
   * existing libraries and APIs to achieve the required test solution.
   */
  public static final String TYPE_LIBRARY = "LIBRARY";

  /**
   * Debugging: Assesses a developers ability in debugging existing code that has a series of known
   * issues that must be fixed for it to function correctly.
   */
  public static final String TYPE_DEBUGGING = "DEBUGGING";

  @ApiModelProperty("The unique identifier for this task")
  private String taskId;

  @ApiModelProperty("The primary skill tested by this task")
  private String type;

  @ApiModelProperty("User readable name for the test")
  private String name;

  @ApiModelProperty("The set of types of result that will come back from testing this task")
  private Set<Criterion> criteria;

  @ApiModelProperty("The name of the docker image to use when testing this image")
  private String image;

  @ApiModelProperty("Test difficulty (values to be determined)")
  private String difficulty;

  @ApiModelProperty("The recommended time in minutes to allocate to this task")
  private int recommendedTimeMinutes;

  @ApiModelProperty("The programming language being tested (currently only java)")
  private String language;

  @ApiModelProperty(
      "The problem statement as an HTML fragment. Use /n to delimit new line characters if needed")
  private String problemStatement;

  @ApiModelProperty("Container restrictions on the compilation step")
  private ContainerRestrictions compilationRestrictions;

  @ApiModelProperty("Container restrictions on the test harness")
  private ContainerRestrictions harnessRestrictions;

  @ApiModelProperty("Container restrictions on the validator")
  private ContainerRestrictions validatorRestrictions;

  @ApiModelProperty(
      "Container restrictions on the task compilation step (i.e. compiling the test itself")
  private ContainerRestrictions taskCompilationRestrictions;

  @ApiModelProperty(
      "List of filenames (relative to the root of the project) to open as a starting point of the "
          + "task")
  private List<String> startingPointFiles;

  public TaskInfo(String taskId) {
    super();
    this.taskId = taskId;
    this.compilationRestrictions = ContainerRestrictions.candidateRestriction(null);
    this.harnessRestrictions = ContainerRestrictions.candidateRestriction(null);
    this.validatorRestrictions = ContainerRestrictions.candidateRestriction(null);
    this.taskCompilationRestrictions = ContainerRestrictions.authorRestriction(null);
  }

  @JsonCreator
  public TaskInfo(
      @JsonProperty("type") String type,
      @JsonProperty("name") String name,
      @JsonProperty("criteria") Set<Criterion> criteria,
      @JsonProperty("image") String image,
      @JsonProperty("difficulty") String difficulty,
      @JsonProperty("recommendedTimeMinutes") int recommendedTimeMinutes,
      @JsonProperty("language") String language,
      @JsonProperty("problemStatement") String problemStatement,
      @JsonProperty("compilationRestrictions") ContainerRestrictions compilationRestrictions,
      @JsonProperty("harnessRestrictions") ContainerRestrictions harnessRestrictions,
      @JsonProperty("validatorRestrictions") ContainerRestrictions validatorRestrictions,
      @JsonProperty("taskCompilationRestrictions")
          ContainerRestrictions taskCompilationRestrictions,
      @JsonProperty("startingPointFiles") List<String> startingPointFiles) {
    super();
    this.type = type;
    this.name = name;
    this.criteria = criteria;
    this.image = image;
    this.difficulty = difficulty;
    this.recommendedTimeMinutes = recommendedTimeMinutes;
    this.language = language;
    this.problemStatement = problemStatement;
    this.compilationRestrictions =
        ContainerRestrictions.candidateRestriction(compilationRestrictions);
    this.harnessRestrictions = ContainerRestrictions.candidateRestriction(harnessRestrictions);
    this.validatorRestrictions = ContainerRestrictions.candidateRestriction(validatorRestrictions);
    this.taskCompilationRestrictions =
        ContainerRestrictions.authorRestriction(taskCompilationRestrictions);
    this.startingPointFiles = startingPointFiles;
  }

  public ContainerRestrictions getCompilationRestrictions() {
    return compilationRestrictions;
  }

  public ContainerRestrictions getHarnessRestrictions() {
    return harnessRestrictions;
  }

  public ContainerRestrictions getValidatorRestrictions() {
    return validatorRestrictions;
  }

  public ContainerRestrictions getTaskCompilationRestrictions() {
    return taskCompilationRestrictions;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public Set<Criterion> getCriteria() {
    return criteria;
  }

  public String getImage() {
    return image;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public int getRecommendedTimeMinutes() {
    return recommendedTimeMinutes;
  }

  public String getLanguage() {
    return language;
  }

  public String getProblemStatement() {
    return problemStatement;
  }

  public List<String> getStartingPointFiles() {
    return startingPointFiles;
  }

    public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public void setStartingPointFiles(List<String> startingPointFiles) {
    this.startingPointFiles = startingPointFiles;
  }
}
