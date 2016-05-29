package uk.ac.cam.cl.dtg.teaching.pottery.worker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskManager;

@Singleton
public class Worker {

	public static Logger LOG = LoggerFactory.getLogger(Worker.class);
	
	private ExecutorService threadPool;
	
	private TaskManager taskManager;
	
	private RepoFactory repoFactory;

	private ContainerManager containerManager;
	
	private Database database;
	
	@Inject
	public Worker(TaskManager taskManager, RepoFactory repoFactory, ContainerManager containerManager, Database database) {
		super();
		this.threadPool = Executors.newFixedThreadPool(2);
		this.taskManager = taskManager;
		this.repoFactory = repoFactory;
		this.containerManager = containerManager;
		this.database = database;
	}

	public void schedule(Job j) {
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					j.execute(taskManager,repoFactory,containerManager,database);
				} catch (Exception e) {
					LOG.error("Unhandled exception in worker",e);
				}
			}			
		});
	}
	
	@PreDestroy
	public void stop() {
		threadPool.shutdownNow();
	}

}
