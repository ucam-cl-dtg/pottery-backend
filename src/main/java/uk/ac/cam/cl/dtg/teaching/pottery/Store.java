package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Store {

	private static final Logger log = LoggerFactory.getLogger(Store.class);
	
	private static File STORAGE_LOCATION = new File("/local/scratch/acr31/pottery-problems");
		
	public Map<String,Task> tasks = new HashMap<String,Task>();
	
	public Map<String,Repo> repos = new ConcurrentHashMap<String,Repo>();
	
	public Map<String,Submission> submission = new ConcurrentHashMap<String,Submission>();
	
	public LinkedBlockingQueue<Submission> testingQueue = new LinkedBlockingQueue<Submission>();
	
	private AtomicBoolean running = new AtomicBoolean(true);
	
	private Thread worker;

	
	public Store(final SourceManager repoManager) {
		ObjectMapper o = new ObjectMapper();
		for(File f : STORAGE_LOCATION.listFiles()) {
			if (f.getName().startsWith(".")) continue;			
			File taskSpecFile = new File(f,"task.json");
			if (taskSpecFile.exists()) {
				try {
					Task t = o.readValue(taskSpecFile,Task.class);
					tasks.put(t.getTaskId(), t);
				} catch (IOException e) {
					log.warn("Failed to load task "+taskSpecFile,e);
				}
			}
			else {
				log.warn("Failed to find task.json file for task in {}",taskSpecFile);
			}
		}
		
		worker = new Thread() {
			@Override
			public void run() {
				while(running.get()) {
					try {
						Submission s = testingQueue.take();
						try {
							Repo r = repos.get(s.getRepoId());
							log.error("Testing repo "+s.getRepoId()+":"+s.getTag()+ " on task "+r.getTaskId());

							repoManager.cloneForTesting(s.getRepoId(), s.getTag());
							// clone the repo with the tag and put it in a suitable container
							// with the testing programs
							
							// read the name of the container needed from the test
							// mount within it:
							// the clone of the repo
							// copy the compile-solution.sh script into it
							// run compile-solution.sh [cloneDir]
							// capture the output
							
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

	public static File getSkeletonLocation(String taskId) {
		return new File(new File(STORAGE_LOCATION,taskId),"skeleton");
	}
}
