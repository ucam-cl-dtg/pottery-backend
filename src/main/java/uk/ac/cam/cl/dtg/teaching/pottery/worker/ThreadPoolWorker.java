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

import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.model.JobStatus;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;

public class ThreadPoolWorker implements Worker {

  protected static final Logger LOG = LoggerFactory.getLogger(ThreadPoolWorker.class);

  private final SortedSet<JobStatus> queue = new TreeSet<>();
  private final TaskIndex taskIndex;
  private final RepoFactory repoFactory;
  private final ContainerManager containerManager;
  private final Database database;
  private final Object smoothedWaitTimeMutex = new Object();

  private ExecutorService threadPool;
  private int numThreads;
  private long smoothedWaitTime = 0;
  private String workerName;

  /** Creates a new ThreadPoolWorker with one thread in the pool. */
  @Inject
  public ThreadPoolWorker(
      TaskIndex taskIndex,
      RepoFactory repoFactory,
      ContainerManager containerManager,
      Database database,
      @Named(Worker.WORKER_NAME) String workerName,
      @Named(Worker.INITIAL_POOL_SIZE) int initialPoolSize) {
    super();
    this.threadPool = Executors.newFixedThreadPool(1);
    this.numThreads = initialPoolSize;
    this.taskIndex = taskIndex;
    this.repoFactory = repoFactory;
    this.containerManager = containerManager;
    this.database = database;
    this.workerName = workerName;
  }

  @Override
  public synchronized void rebuildThreadPool(int numThreads) {
    final List<Runnable> pending = this.threadPool.shutdownNow();
    try {
      this.threadPool.awaitTermination(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    this.threadPool = Executors.newFixedThreadPool(numThreads);
    this.numThreads = numThreads;
    for (Runnable r : pending) {
      this.threadPool.execute(r);
    }
  }

  @Override
  public synchronized int getNumThreads() {
    return numThreads;
  }

  @Override
  public List<JobStatus> getQueue() {
    synchronized (queue) {
      return new LinkedList<>(queue);
    }
  }

  /**
   * Schedule a sequence of jobs.
   *
   * @param jobs the jobs to be run in sequence (if a job fails then we stop there)
   */
  @Override
  public synchronized void schedule(Job... jobs) {
    threadPool.execute(new JobIteration(jobs, 0, false, System.currentTimeMillis()));
  }

  @Override
  public long getSmoothedWaitTime() {
    synchronized (smoothedWaitTimeMutex) {
      return smoothedWaitTime;
    }
  }

  @Override
  public void stop() {
    LOG.info("Shutting down thread pool for " + workerName);
    threadPool.shutdownNow();
  }

  private class JobIteration implements Runnable {
    private Job[] jobs;
    private int index;
    private JobStatus status;
    private boolean withPause;
    private long enqueueTime;

    /**
     * Create an iteration ready to execute the nth item of the jobs list.
     *
     * @param jobs an array of jobs to execute
     * @param index the index of the job to execute from the array
     * @param withPause set to true if you want a 2 second pause before running the job (e.g. for
     *     retries)
     */
    JobIteration(Job[] jobs, int index, boolean withPause, long enqeueTime) {
      super();
      this.jobs = jobs;
      this.index = index;
      this.withPause = withPause;
      this.status = new JobStatus(jobs[index].getDescription(), workerName);
      this.enqueueTime = enqeueTime;
      synchronized (queue) {
        queue.add(status);
      }
    }

    @Override
    public void run() {
      status.setStatus(JobStatus.STATUS_RUNNING);
      long startTime = System.currentTimeMillis();
      if (withPause) {
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          LOG.error("Interrupted", e);
        }
      }
      try {
        int result = jobs[index].execute(taskIndex, repoFactory, containerManager, database);
        if (result == Job.STATUS_OK) {
          if (index < jobs.length - 1) {
            threadPool.execute(new JobIteration(jobs, index + 1, false, enqueueTime));
          }
        } else if (result == Job.STATUS_RETRY) {
          threadPool.execute(new JobIteration(jobs, index, true, enqueueTime));
        }

        if ((result == Job.STATUS_OK || result == Job.STATUS_FAILED) && index == 0) {
          // We've run the first step to completion so update the waitTime
          synchronized (smoothedWaitTimeMutex) {
            smoothedWaitTime =
                ((startTime - enqueueTime) >> 3) + smoothedWaitTime - (smoothedWaitTime >> 3);
          }
        }

      } catch (Exception e) {
        LOG.error("Unhandled exception in worker", e);
      } finally {
        synchronized (queue) {
          queue.remove(status);
        }
      }
    }
  }
}
