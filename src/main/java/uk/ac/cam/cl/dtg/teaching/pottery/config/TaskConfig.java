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

package uk.ac.cam.cl.dtg.teaching.pottery.config;

import java.io.File;
import javax.inject.Inject;
import javax.inject.Named;

public class TaskConfig {

  private File taskPrefix;

  @Inject
  public TaskConfig(@Named("localStoragePrefix") String prefix) {
    this.taskPrefix = new File(prefix, "tasks");
  }

  public File getTaskDefinitionRoot() {
    return new File(taskPrefix, "def");
  }

  public File getLocalTaskDefinitionDir(String taskId) {
    return new File(getTaskDefinitionRoot(), taskId);
  }

  public File getTaskCopyRoot() {
    return new File(taskPrefix, "copy");
  }

  public File getTaskCopyDir(String copyId) {
    return new File(getTaskCopyRoot(), copyId);
  }

  public File getSolutionsDir(String copyId) {
    return new File(getTaskCopyDir(copyId), "solution");
  }

  public File getStepsDir(String copyId) { return new File(getTaskCopyDir(copyId), "steps"); }

  public File getSkeletonsDir(String copyId) {
    return new File(getTaskCopyDir(copyId), "skeleton");
  }

  public File getSkeletonDir(String copyId, String variant) { return new File(getSkeletonsDir(copyId), variant); }
}
