/**
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

import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.Stoppable;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;

@Singleton
public class Worker implements Stoppable {

	protected static final Logger LOG = LoggerFactory.getLogger(Worker.class);
	
	private ExecutorService threadPool;
	
	private TaskIndex taskIndex;
	
	private RepoFactory repoFactory;

	private ContainerManager containerManager;
	
	private Database database;
		
	@Inject
	public Worker(TaskIndex taskIndex, RepoFactory repoFactory, ContainerManager containerManager, Database database) {
		super();
		this.threadPool = Executors.newFixedThreadPool(1);
		this.taskIndex = taskIndex;
		this.repoFactory = repoFactory;
		this.containerManager = containerManager;
		this.database = database;
	}

	public synchronized void rebuildThreadPool(int numThreads) {
		List<Runnable> pending = this.threadPool.shutdownNow();
		try {
			this.threadPool.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		this.threadPool = Executors.newFixedThreadPool(numThreads);
		for(Runnable r : pending) {
			this.threadPool.execute(r);
		}
	}
	
	public List<JobStatus> getQueue() {
		synchronized (queue) {
			return new LinkedList<>(queue);
		}
	}
	
	/**
	 * Schedule a sequence of jobs
	 * @param jobs the jobs to be run in sequence (if a job fails then we stop there)
	 */
	public synchronized void schedule(Job... jobs) {
		threadPool.execute(new JobIteration(jobs,0));
	} 
	
	private final SortedSet<JobStatus> queue = new TreeSet<JobStatus>();
	
	private class JobIteration implements Runnable {
		private Job[] jobs;
		private int index;
		private JobStatus status;	
		
		public JobIteration(Job[] jobs, int index) {
			super();
			this.jobs = jobs;
			this.index = index;
			this.status = new JobStatus(jobs[index].getDescription());
			synchronized (queue) {
				queue.add(status);
			}
		}
		
		@Override
		public void run() {
			status.setStatus(JobStatus.STATUS_RUNNING);
			try {
				boolean result = jobs[index].execute(taskIndex, repoFactory, containerManager, database);
				if (result && index < jobs.length -1) {
					threadPool.execute(new JobIteration(jobs,index+1));
				}
			} catch (Exception e) {
				LOG.error("Unhandled exception in worker",e);
			} finally {
				synchronized (queue) {
					queue.remove(status);
				}
			}
		}
	}
	
	@Override
	public void stop() {
		LOG.info("Shutting down thread pool");
		threadPool.shutdownNow();		
	}

}
