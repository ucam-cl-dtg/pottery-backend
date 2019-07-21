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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;

abstract class Binding {

  private static final Pattern COMMAND_TOKENIZER =
      Pattern.compile("([^\"' ]|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')+");

  static final String POTTERY_BINARIES_PATH = "/pottery-binaries";
  static final String IMAGE_BINDING = "IMAGE";
  static final String SUBMISSION_BINDING = "SUBMISSION";
  static final String VARIANT_BINDING = "VARIANT";
  static final String TASK_BINDING = "TASK";
  static final String STEP_BINDING = "STEP";
  static final String SHARED_BINDING = "SHARED";
  static final String MUTATION_ID_BINDING = "MUTATION";

  // e.g. /taskRoot/common - shared between all steps and variants
  static final String COMMON_BINDING = "COMMON";

  private static Pattern bindingRegex = Pattern.compile("@([a-zA-Z_][-a-zA-Z_0-9]*)@");

  static ExecutionConfig.Builder applyBindings(
      String command, ImmutableMap<String, Binding> bindings, Function<String, Binding> getBinding)
      throws ContainerExecutionException {
    ExecutionConfig.Builder builder = ExecutionConfig.builder();
    StringBuilder finalCommand = new StringBuilder();

    Map<String, Binding> mutableBindings = new HashMap<String, Binding>(bindings);

    int previousMatchEnd = 0;
    Matcher regexMatcher = bindingRegex.matcher(command);
    while (regexMatcher.find()) {
      String name = regexMatcher.group(1);
      Binding binding;
      if (mutableBindings.containsKey(name)) {
        binding = mutableBindings.get(name);
      } else {
        binding = getBinding.apply(name);
        if (binding != null) {
          mutableBindings.put(name, binding);
        } else {
          throw new ContainerExecutionException(
              "Couldn't find a binding called " + name + " for command " + command);
        }
      }
      binding.applyBinding(builder, name);
      int currentMatchStart = regexMatcher.start();
      finalCommand.append(command.substring(previousMatchEnd, currentMatchStart));
      finalCommand.append(binding.getMountPoint(name));
      previousMatchEnd = regexMatcher.end();
    }
    finalCommand.append(command.substring(previousMatchEnd));

    Matcher matcher = COMMAND_TOKENIZER.matcher(finalCommand);
    while (matcher.find()) {
      builder.addCommand(matcher.group());
    }
    return builder;
  }

  abstract String getMountPoint(String name);

  ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name)
      throws ContainerExecutionException {
    return builder;
  }

  static class FileBinding extends Binding {
    private final File file;
    private final boolean readWrite;
    private final String internalMountPath;
    private boolean needsApplying;

    FileBinding(File file, boolean readWrite, String internalMountPath) {
      this.file = file;
      this.readWrite = readWrite;
      this.internalMountPath = internalMountPath;
      this.needsApplying = true;
    }

    @Override
    ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name) {
      if (needsApplying) {
        needsApplying = false;

        return builder.addPathSpecification(
            PathSpecification.create(file, getMountPoint(name), readWrite));
      } else {
        return builder;
      }
    }

    @Override
    String getMountPoint(String name) {
      return internalMountPath + "/" + name;
    }
  }

  static class TemporaryFileBinding extends Binding {
    private final File containerTempDir;
    private final String content;
    private final String internalMountPath;
    private boolean needsApplying;

    TemporaryFileBinding(File containerTempDir, String content, String internalMountPath) {
      this.containerTempDir = containerTempDir;
      this.content = content;
      this.internalMountPath = internalMountPath;
      this.needsApplying = true;
    }

    @Override
    ExecutionConfig.Builder applyBinding(ExecutionConfig.Builder builder, String name)
        throws ContainerExecutionException {
      if (needsApplying) {
        needsApplying = false;

        File stepFile = new File(containerTempDir, name);
        try {
          Files.asCharSink(stepFile, Charset.defaultCharset()).write(content);
        } catch (IOException e) {
          throw new ContainerExecutionException(
              "Couldn't create temporary file for binding " + name, e);
        }
        return builder.addPathSpecification(
            PathSpecification.create(stepFile, getMountPoint(name), false));
      } else {
        return builder;
      }
    }

    @Override
    String getMountPoint(String name) {
      return internalMountPath + "/" + name;
    }
  }

  static class ImageBinding extends Binding {
    private final String path;

    ImageBinding(String path) {
      this.path = path;
    }

    @Override
    String getMountPoint(String name) {
      return path;
    }
  }

  static class TextBinding extends Binding {
    private final String text;

    TextBinding(String text) {
      this.text = text;
    }

    /** Technically, this isn't a mount point, it's just a parameter. */
    @Override
    String getMountPoint(String name) {
      return text;
    }
  }
}
