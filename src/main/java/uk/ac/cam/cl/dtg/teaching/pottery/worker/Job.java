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
package uk.ac.cam.cl.dtg.teaching.pottery.worker;

import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;

public interface Job {

  int STATUS_OK = 0;
  int STATUS_FAILED = 1;
  int STATUS_RETRY = 2;

  /**
   * Run the required task.
   *
   * @param taskIndex the index of the task to execute
   * @param repoFactory repository factory class instance
   * @param containerManager manager for container engine
   * @param database class for database instance
   * @return a status code indicating what should be done
   */
  int execute(
      TaskIndex taskIndex,
      RepoFactory repoFactory,
      ContainerManager containerManager,
      Database database);

  /**
   * Textual description of the job.
   *
   * @return the job description
   */
  String getDescription();
}
