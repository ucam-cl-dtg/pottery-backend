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
package uk.ac.cam.cl.dtg.teaching.pottery.worker;

import com.google.common.collect.ImmutableList;
import java.util.List;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;

public class BlockingWorker implements Worker {

  private final TaskIndex taskIndex;
  private final RepoFactory repoFactory;
  private final ContainerManager containerManager;
  private final Database database;

  public BlockingWorker(
      TaskIndex taskIndex,
      RepoFactory repoFactory,
      ContainerManager containerManager,
      Database database) {
    this.taskIndex = taskIndex;
    this.repoFactory = repoFactory;
    this.containerManager = containerManager;
    this.database = database;
  }

  @Override
  public void rebuildThreadPool(int numThreads) {}

  @Override
  public int getNumThreads() {
    return 0;
  }

  @Override
  public List<JobStatus> getQueue() {
    return ImmutableList.of();
  }

  @Override
  public void schedule(Job... jobs) {
    for (Job j : jobs) {
      int result = j.execute(taskIndex, repoFactory, containerManager, database);
      if (result != Job.STATUS_OK) {
        return;
      }
    }
  }

  @Override
  public long getSmoothedWaitTime() {
    return 0;
  }

  @Override
  public void stop() {}
}
