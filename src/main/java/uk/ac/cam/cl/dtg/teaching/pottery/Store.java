package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class Store {

	private static final Logger log = LoggerFactory.getLogger(Store.class); 
			
	public Map<String,Repo> repos = new ConcurrentHashMap<String,Repo>();
	
	public Map<String,Submission> submission = new ConcurrentHashMap<String,Submission>();
	
	public LinkedBlockingQueue<Submission> testingQueue = new LinkedBlockingQueue<Submission>();
	
	private AtomicBoolean running = new AtomicBoolean(true);
	
	private Thread worker;

	@Inject
	public Store(final SourceManager repoManager, final TaskManager taskManager) {
		
		worker = new Thread() {
			@Override
			public void run() {
				while(running.get()) {
					try {
						Submission s = testingQueue.take();
						try {
							Repo r = repos.get(s.getRepoId());
							Task t = taskManager.getTask(r.getTaskId());
							log.error("Testing repo "+s.getRepoId()+":"+s.getTag()+ " on task "+r.getTaskId());

							// clone the submitted code repo with the given tag
							File submittedCodeLocation = repoManager.cloneForTesting(s.getRepoId(), s.getTag());
							
							// get the name of the compile directory from the problem source

							
							// get the name of the container image from the problem spec
							// mount the clone of the repo within it
							// mount the compile directory within it
							// run compile-solution.sh in it passing the clone dir
							
							
							// start a new container with mounts
							// clone of the repo
							// the harness directory from the test							
							// run-harness.sh [cloneDir] [harnessDir]
							// capture the output
							
							// start a new container with mounts
							// the validator directory from the test
							// run-validator.sh [validatorDir]
							// pipe the previous output into it
							// capture the output
							

							Thread.sleep(5000);
						}
						catch (IOException|RepoException e) {
							log.error("Failed to start test",e);
						}
						finally {
							s.setStatus(Submission.STATUS_COMPLETE);
							s.setResult(new Result());
						}
					}
					catch (InterruptedException e) {} 
				}
			}
			
		};
		worker.start();
		
	}
	
	@PreDestroy
	public void stop() {
		log.error("STOP called");
		running.set(false);
		worker.interrupt();
	}

}
