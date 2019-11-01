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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;

public class UncontainerImpl implements ContainerBackend {

  private static final File TEMP_DIR = Files.createTempDir();

  private volatile boolean block = false;

  private volatile boolean blocked = true;

  @Override
  public ApiStatus getApiStatus() {
    return ApiStatus.OK;
  }

  @Override
  public long getSmoothedCallTime() {
    return 0;
  }

  @Override
  public String getVersion() {
    return "Uncontainer:1.0.0";
  }

  @Override
  public void setTimeoutMultiplier(int multiplier) {}

  @Override
  public String getInternalMountPath() {
    return TEMP_DIR.getAbsolutePath();
  }

  @Override
  public ContainerExecResponse executeContainer(ExecutionConfig executionConfig)
      throws ApiUnavailableException {
    try {
      ImmutableList<String> commands =
          executionConfig.command().stream()
              .map(
                  command -> {
                    for (PathSpecification pathSpecification :
                        executionConfig.pathSpecification()) {
                      if (command.startsWith(pathSpecification.container().getPath())) {
                        command =
                            pathSpecification.host().getPath()
                                + command.substring(
                                    pathSpecification.container().getPath().length());
                      }
                    }
                    return command;
                  })
              .collect(toImmutableList());
      ProcessBuilder processBuilder = new ProcessBuilder(commands);
      Map<String, String> env = processBuilder.environment();
      env.put("LOCAL_USER_ID", String.valueOf(executionConfig.localUserId()));
      env.put(
          "RAM_LIMIT_MEGABYTES",
          String.valueOf(executionConfig.containerRestrictions().getDiskWriteLimitMegabytes()));
      Process process = processBuilder.start();
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      Thread copyThread =
          new Thread(
              () -> {
                try {
                  IOUtils.copy(process.getInputStream(), bos);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
      copyThread.start();
      process.waitFor();
      copyThread.join();
      String output = new String(bos.toByteArray());
      synchronized (this) {
        while (block) {
          blocked = true;
          this.notifyAll();
          this.wait();
        }
        blocked = false;
        this.notifyAll();
      }
      return ContainerExecResponse.create(
          process.exitValue() == 0
              ? ContainerExecResponse.Status.COMPLETED
              : ContainerExecResponse.Status.FAILED_EXITCODE,
          output,
          0,
          "no-container");
    } catch (IOException | InterruptedException e) {
      throw new ApiUnavailableException(e);
    }
  }

  public synchronized void block() {
    block = true;
  }

  public synchronized void unblock() {
    block = false;
    this.notifyAll();
  }

  public synchronized void waitForBlocked() {
    while (!blocked && block) {
      try {
        this.wait();
      } catch (InterruptedException e) {
        // ignore
      }
    }
  }

  @Override
  public void stop() {
    TEMP_DIR.delete();
  }
}
