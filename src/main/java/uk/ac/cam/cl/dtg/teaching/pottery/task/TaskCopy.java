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

package uk.ac.cam.cl.dtg.teaching.pottery.task;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TwoPhaseLatch;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidTaskSpecificationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;

/**
 * A task copy is a checkout of a task. Its made by copying files out of the taskdef git repo. Task
 * copies are readonly - you can't change the files in them. A task copy contains a TwoPhaseLatch,
 * when you start using a TaskCopy you acquire access through the latch and release it when you call
 * close (use try with resources). Anyone who wants to replace this TaskCopy with another one must
 * wait until all the latches are released.
 *
 * @author acr31
 */
public class TaskCopy implements AutoCloseable {

  private String copyId;
  private TaskConfig config;
  private TaskInfo info;
  private TwoPhaseLatch latch = new TwoPhaseLatch();

  /**
   * Instances of this object are created by the TaskCopyBuilder object. Its up to the
   * TaskCopyBuilder object to ensure that there is only instance of this object for each copyId
   *
   * @param taskId the ID of the task itself
   * @param copyId the unique ID for this copy
   * @param config information for tasks
   * @throws InvalidTaskSpecificationException if we can't load the task specification
   * @throws TaskStorageException if the task cannot be accessed
   */
  TaskCopy(String taskId, String copyId, TaskConfig config)
      throws InvalidTaskSpecificationException, TaskStorageException {
    super();
    this.copyId = copyId;
    this.config = config;
    this.info = TaskInfos.load(taskId, config.getTaskCopyDir(copyId));
  }

  public String getCopyId() {
    return copyId;
  }

  public TaskInfo getInfo() {
    return info;
  }

  public File getLocation() {
    return config.getTaskCopyDir(copyId);
  }

  public File getStepsLocation() { return config.getStepsDir(copyId); }
  public File getSolutionsLocation() { return config.getSolutionsDir(copyId); }

  /*public List<String> listSkeleton(String variant) throws TaskStorageException {
    File sourceLocation = config.getSkeletonDir(copyId, variant);
    if (!sourceLocation.exists()) {
      return new LinkedList<>();
    }

    try {
      List<String> result = new LinkedList<>();
      Files.walkFileTree(
          sourceLocation.toPath(),
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              Path localLocation = sourceLocation.toPath().relativize(file);
              result.add(localLocation.toString());
              return FileVisitResult.CONTINUE;
            }
          });
      return result;
    } catch (IOException e) {
      throw new TaskStorageException(
          "Failed to access skeleton files for task "
              + info.getTaskId()
              + " stored in copy "
              + copyId,
          e);
    }
  }*/

  /**
   * Copy the skeleton files from this task copy to the target directory given (this will be in a
   * candidates repo).
   */
  public ImmutableList<String> copySkeleton(File destination, String variant) throws IOException {
    return FileUtil.copyFilesRecursively(config.getSkeletonDir(copyId, variant), destination);
  }

  public boolean acquire() {
    return latch.acquire();
  }

  /** Delete this copy from the disk. */
  void destroy() throws IOException, InterruptedException {
    latch.await();
    FileUtil.deleteRecursive(config.getTaskCopyDir(copyId));
  }

  @Override
  public void close() {
    latch.release();
  }
}
