package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class Store {

	private static final Logger log = LoggerFactory.getLogger(Store.class);
	
	public Map<String,Task> tasks = ImmutableMap.of(
			"3d0e6004-71fa-42b4-a304-d7236e0066a3",
			new Task("3d0e6004-71fa-42b4-a304-d7236e0066a3",
					"<h1>String reverse</h1><p>The function <texttt>reverse</texttt> should take an array of characters and reverse their order storing the result in the original array.  Complete the implementation of this function.</p>")
			.setCriteria(Criterion.CORRECTNESS,Criterion.ROBUSTNESS),
			"133fd4db-905d-4b09-a37f-abb4fb463d2a",
			new Task("133fd4db-905d-4b09-a37f-abb4fb463d2a",
					"<h1>Averages</h1><p>The function <texttt>mean</texttt> should take an array of doubles and compute their mean. Complete the implementation of this function.</p>")
			.setCriteria(Criterion.CORRECTNESS, Criterion.ROBUSTNESS)
			);
	
	public Map<String,Progress> progress = new ConcurrentHashMap<String, Progress>();
	
	public Map<String,Submission> submission = new ConcurrentHashMap<String,Submission>();
	
	public LinkedBlockingQueue<Submission> testingQueue = new LinkedBlockingQueue<Submission>();
	
	private AtomicBoolean running = new AtomicBoolean(true);
	
	private Thread worker;
	
	public Store() {
		
		worker = new Thread() {
			@Override
			public void run() {
				while(running.get()) {
					try {
						Submission s = testingQueue.take();
						Thread.sleep(5000);
						s.getStatus().setStatus(Status.STATUS_COMPLETE);
						s.setResult(new Result());
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
