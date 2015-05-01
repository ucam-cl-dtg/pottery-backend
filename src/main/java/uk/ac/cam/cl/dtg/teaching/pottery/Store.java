package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

import com.google.common.collect.ImmutableMap;

public class Store {

	private static final Logger log = LoggerFactory.getLogger(Store.class);

	
	
	public Map<String,Task> tasks = ImmutableMap.of(
			"3d0e6004-71fa-42b4-a304-d7236e0066a3",
			new Task()
			.withTaskId("3d0e6004-71fa-42b4-a304-d7236e0066a3")
			.withDescription("<h1>String reverse</h1><p>The function <texttt>reverse</texttt> should take an array of characters and reverse their order storing the result in the original array.  Complete the implementation of this function.</p>")
			.withCriteria(Criterion.CORRECTNESS,Criterion.ROBUSTNESS),

			"133fd4db-905d-4b09-a37f-abb4fb463d2a",
			new Task()
			.withTaskId("133fd4db-905d-4b09-a37f-abb4fb463d2a")
			.withDescription("<h1>Averages</h1><p>The function <texttt>mean</texttt> should take an array of doubles and compute their mean. Complete the implementation of this function.</p>")
			.withCriteria(Criterion.CORRECTNESS, Criterion.ROBUSTNESS)
			);
	
	public Map<String,Repo> repos = new ConcurrentHashMap<String,Repo>();
	
	public Map<String,Submission> submission = new ConcurrentHashMap<String,Submission>();
	
	public LinkedBlockingQueue<Submission> testingQueue = new LinkedBlockingQueue<Submission>();
	
	private AtomicBoolean running = new AtomicBoolean(true);
	
	private Thread worker;
	
	public Store(final SourceManager repoManager) {
		
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
