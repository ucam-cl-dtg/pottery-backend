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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.wordnik.swagger.annotations.ApiModelProperty;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;

public class TaskDetail {

  @ApiModelProperty("The unique identifier for this task")
  private String taskId;

  @ApiModelProperty("The primary skill tested by this task")
  private String type;

  @ApiModelProperty("User readable name for the test")
  private String name;

  @ApiModelProperty("The set of types of result that will come back from testing this task")
  private Set<String> criteria;

  @ApiModelProperty("Test difficulty (values to be determined)")
  private String difficulty;

  @ApiModelProperty("The recommended time in minutes to allocate to this task")
  private int recommendedTimeMinutes;

  @ApiModelProperty(
      "The problem statement as an HTML fragment. Use /n to delimit new line characters if needed")
  private String problemStatement;

  @ApiModelProperty("List of questions to ask the candidate about the task")
  private List<String> questions;

  @ApiModelProperty("Variants supported by this task")
  private Set<String> variants;

  @ApiModelProperty("Internal tests that should be run when the task is registered")
  private Map<String, List<Testcase>> taskTests;

  @ApiModelProperty("List of executions needed to be run to compile this task")
  private List<Execution> taskCompilation;

  @ApiModelProperty("Map of named steps that can be used in actions")
  private Map<String, Step> steps;

  @ApiModelProperty("Actions that can be requested for this task")
  private Map<String, Action> actions;

  @ApiModelProperty("Instructions for parameterising this task")
  @Nullable
  private Parameterisation parameterisation;

  @ApiModelProperty("Properties to pass to frontend for this task")
  private Map<String, String> properties;

  public TaskDetail(String taskId) {
    super();
    this.taskId = taskId;
  }

  @JsonCreator
  public TaskDetail(
      @JsonProperty("type") String type,
      @JsonProperty("name") String name,
      @JsonProperty("criteria") Set<String> criteria,
      @JsonProperty("difficulty") String difficulty,
      @JsonProperty("recommendedTimeMinutes") int recommendedTimeMinutes,
      @JsonProperty("problemStatement") String problemStatement,
      @JsonProperty("questions") List<String> questions,
      @JsonProperty("variants") Set<String> variants,
      @JsonProperty("taskTests") Map<String, List<Testcase>> taskTests,
      @JsonProperty("taskCompilation") List<Execution> taskCompilation,
      @JsonProperty("steps") Map<String, Step> steps,
      @JsonProperty("actions") Map<String, Action> actions,
      @JsonProperty("parameterisation") Parameterisation parameterisation,
      @JsonProperty("properties") Map<String, String> properties)
      throws InvalidTaskSpecificationException {
    super();
    this.taskId = "UNSET";
    this.type = orElse(type, "GENERIC");
    this.name = orThrow(name, "Task name");
    this.criteria = orElse(criteria, ImmutableSet.of());
    this.difficulty = orElse(difficulty, "GENERIC");
    this.recommendedTimeMinutes = recommendedTimeMinutes;
    this.problemStatement = orThrow(problemStatement, "Problem statement");
    this.questions = orElse(questions, ImmutableList.of());
    this.variants = orThrow(variants, "Variants");
    this.taskTests = orElse(taskTests, ImmutableMap.of());
    this.taskCompilation =
        orElse(taskCompilation, ImmutableList.<Execution>of()).stream()
            .map(
                e ->
                    e.withDefaultContainerRestriction(
                        ContainerRestrictions.DEFAULT_AUTHOR_RESTRICTIONS))
            .collect(Collectors.toList());
    this.steps =
        Maps.transformValues(
            orElse(steps, ImmutableMap.<String, Step>of()),
            v ->
                v.withDefaultContainerRestriction(
                    ContainerRestrictions.DEFAULT_CANDIDATE_RESTRICTIONS));
    this.actions = orElse(actions, ImmutableMap.of());
    Task.LOG.info(
        "Building TaskDetail with name "
            + name
            + " with parameterisation count "
            + (parameterisation != null ? parameterisation.getCount() : "none"));
    if (parameterisation != null) {
      this.parameterisation =
          parameterisation.withDefaultContainerRestrictions(
              ContainerRestrictions.DEFAULT_AUTHOR_RESTRICTIONS);
    } else {
      this.parameterisation = null;
    }
    this.properties = orElse(properties, ImmutableMap.of());
  }

  private static <T> T orElse(T value, T deflt) {
    if (value == null) {
      return deflt;
    }
    return value;
  }

  private static <T> T orThrow(T value, String name) throws InvalidTaskSpecificationException {
    if (value == null) {
      throw new InvalidTaskSpecificationException("Take specification must include " + name);
    }
    return value;
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

  public Set<String> getCriteria() {
    return criteria;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public int getRecommendedTimeMinutes() {
    return recommendedTimeMinutes;
  }

  public String getProblemStatement() {
    return problemStatement;
  }

  public List<String> getQuestions() {
    return questions;
  }

  public Set<String> getVariants() {
    return variants;
  }

  public Map<String, List<Testcase>> getTaskTests() {
    return taskTests;
  }

  public List<Execution> getTaskCompilation() {
    return taskCompilation;
  }

  public Map<String, Step> getSteps() {
    return steps;
  }

  public Map<String, Action> getActions() {
    return actions;
  }

  @Nullable
  public Parameterisation getParameterisation() {
    return parameterisation;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public TaskInfo toTaskInfo(File taskDirectory) {
    return new TaskInfo(
        taskId,
        type,
        name,
        criteria,
        difficulty,
        recommendedTimeMinutes,
        expandString(problemStatement, taskDirectory),
        parameterisation != null ? parameterisation.getCount() : 0,
        questions,
        variants,
        actions.keySet(),
        Maps.transformValues(properties, p -> expandString(p, taskDirectory)));
  }

  /**
   * Expand templates in the string.
   *
   * <p>Currently the only template supported is file:filename which attempts to load filename
   * relative to the task root directly and replace the contents.
   */
  private static String expandString(String value, File taskDirectory) {
    if (value.startsWith("file:")) {
      try (FileInputStream r = new FileInputStream(new File(taskDirectory, value.substring(5)))) {
        return IOUtils.toString(r, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IOError(e);
      }
    }
    return value;
  }

  /** Read the json file specifying this TaskDetail from disk and parse it into an object. */
  public static TaskDetail load(String taskId, File taskDirectory)
      throws InvalidTaskSpecificationException {
    ObjectMapper o = new ObjectMapper();
    try {
      TaskDetail t = o.readValue(new File(taskDirectory, "task.json"), TaskDetail.class);
      t.setTaskId(taskId);
      return t;
    } catch (IOException e) {
      throw new InvalidTaskSpecificationException(
          "Failed to load task information for task " + taskId, e);
    }
  }

  /** Write this task info back to disk. */
  public static void save(TaskDetail taskInfo, File taskDirectory) throws IOException {
    ObjectMapper o = new ObjectMapper();
    o.writeValue(new File(taskDirectory, "task.json"), taskInfo);
  }
}
