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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;

@RunWith(JUnit4.class)
public class TaskDetailTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void load_readsTaskJson() throws IOException, InvalidTaskSpecificationException {
    // ARRANGE
    File taskJson = temporaryFolder.newFile("task.json");
    try (FileWriter w = new FileWriter(taskJson)) {
      IOUtils.write(
          lines(
              "{", //
              " \"name\": \"Test task\",",
              " \"problemStatement\": \"Test problem statement\",",
              " \"variants\": [\"java\"]",
              "}"),
          w);
    }

    // ACT
    TaskDetail d = TaskDetail.load("test-task-id", temporaryFolder.getRoot());

    // ASSERT
    assertThat(d.getName()).isEqualTo("Test task");
  }

  @Test
  public void toTaskInfo_readsProblemStatementFromFile()
      throws IOException, InvalidTaskSpecificationException {
    // ARRANGE
    TaskDetail taskDetail =
        new TaskDetail(
            "GENERIC",
            "Test task",
            null,
            null,
            0,
            "file:problemStatement.txt",
            null,
            ImmutableSet.of("java"),
            null,
            null,
            null,
            null,
            null,
            null);
    File problemStatement = temporaryFolder.newFile("problemStatement.txt");
    try (FileWriter w = new FileWriter(problemStatement)) {
      IOUtils.write("Problem statement", w);
    }

    // ACT
    TaskInfo taskInfo = taskDetail.toTaskInfo(temporaryFolder.getRoot());

    // ASSERT
    assertThat(taskInfo.getProblemStatement()).isEqualTo("Problem statement");
  }

  private static String lines(String... l) {
    return Arrays.stream(l).collect(Collectors.joining(System.lineSeparator()));
  }
}
