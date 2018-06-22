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
package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;

@RunWith(JUnit4.class)
public class TestBinding {

  private static final String POTTERY_PREFIX_CONTAINER = "/mnt/pottery-prefix-container";
  private File tempDirHost;

  @Before
  public void setup() throws IOException {
    tempDirHost = Files.createTempDir().getCanonicalFile();
  }

  @After
  public void teardown() throws IOException {
    FileUtil.deleteRecursive(tempDirHost);
  }

  @Test
  public void applyBindings_expandsTextBinding() throws ContainerExecutionException {
    // ARRANGE
    String variant = "test-variant";
    String command = String.format("before @%s@ after", Binding.VARIANT_BINDING);
    ImmutableMap<String, Binding> bindings =
        ImmutableMap.of(Binding.VARIANT_BINDING, new Binding.TextBinding(variant));
    ImmutableMap<String, ContainerExecResponse> stepResults = ImmutableMap.of();

    // ACT
    ImmutableList<String> expandedCommand =
        Binding.applyBindings(command, bindings, stepResults, tempDirHost, POTTERY_PREFIX_CONTAINER)
            .command();

    // ASSERT
    assertThat(expandedCommand).containsExactly("before", variant, "after");
  }

  @Test
  public void applyBindings_expandsImageBinding() throws ContainerExecutionException {
    // ARRANGE
    String image = "test-image";
    String command = String.format("before @%s@ after", Binding.IMAGE_BINDING);
    ImmutableMap<String, Binding> bindings =
        ImmutableMap.of(Binding.IMAGE_BINDING, new Binding.ImageBinding(image));
    ImmutableMap<String, ContainerExecResponse> stepResults = ImmutableMap.of();

    // ACT
    ImmutableList<String> expandedCommand =
        Binding.applyBindings(command, bindings, stepResults, tempDirHost, POTTERY_PREFIX_CONTAINER)
            .command();

    // ASSERT
    assertThat(expandedCommand).containsExactly("before", image, "after");
  }

  @Test
  public void applyBindings_expandsFileBinding() throws ContainerExecutionException {
    // ARRANGE
    File codeDirHost = new File("/code-dir-host");
    String command = String.format("before @%s@ after", Binding.SUBMISSION_BINDING);
    ImmutableMap<String, Binding> bindings =
        ImmutableMap.of(
            Binding.SUBMISSION_BINDING,
            new Binding.FileBinding(codeDirHost, /* readWrite = */ true, POTTERY_PREFIX_CONTAINER));
    ImmutableMap<String, ContainerExecResponse> stepResults = ImmutableMap.of();
    String expectedMountPointContainer =
        POTTERY_PREFIX_CONTAINER + "/" + Binding.SUBMISSION_BINDING;

    // ACT
    ExecutionConfig.Builder builder =
        Binding.applyBindings(
            command, bindings, stepResults, tempDirHost, POTTERY_PREFIX_CONTAINER);

    // ASSERT
    assertThat(builder.command()).containsExactly("before", expectedMountPointContainer, "after");
    assertThat(builder.pathSpecification())
        .containsExactly(PathSpecification.create(codeDirHost, expectedMountPointContainer, true));
  }

  @Test
  public void applyBindings_expandsFileBinding_fromPreviousStep()
      throws ContainerExecutionException, IOException {
    // ARRANGE
    ImmutableMap<String, Binding> bindings = ImmutableMap.of();
    String previousStepName = "test-step";
    String previousStepResponse = "test-response";
    ImmutableMap<String, ContainerExecResponse> stepResults =
        ImmutableMap.of(
            previousStepName,
            ContainerExecResponse.create(
                ContainerExecResponse.Status.COMPLETED, previousStepResponse, 0L));
    String command = String.format("before @%s@ after", previousStepName);
    String expectedMountPointContainer = POTTERY_PREFIX_CONTAINER + "/" + previousStepName;
    File expectedTempFileHost = new File(tempDirHost, previousStepName);

    // ACT
    ExecutionConfig.Builder builder =
        Binding.applyBindings(
            command, bindings, stepResults, tempDirHost, POTTERY_PREFIX_CONTAINER);

    // ASSERT
    assertThat(builder.command()).containsExactly("before", expectedMountPointContainer, "after");
    assertThat(builder.pathSpecification())
        .containsExactly(
            PathSpecification.create(expectedTempFileHost, expectedMountPointContainer, false));
    assertThat(MoreFiles.asCharSource(expectedTempFileHost.toPath(), StandardCharsets.UTF_8).read())
        .isEqualTo(previousStepResponse);
  }
}
