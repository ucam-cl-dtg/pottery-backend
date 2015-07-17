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

import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerHelper;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class Store {

	private static final Logger log = LoggerFactory.getLogger(Store.class); 
			
	
	public Map<String,Submission> submission = new ConcurrentHashMap<String,Submission>();
	
	public LinkedBlockingQueue<Submission> testingQueue = new LinkedBlockingQueue<Submission>();
	
	private AtomicBoolean running = new AtomicBoolean(true);
	
	private Thread worker;

	@Inject
	public Store(final SourceManager sourceManager, final TaskManager taskManager, final DockerApi docker, final Database database) {
		
		worker = new Thread() {
			@Override
			public void run() {
				while(running.get()) {
					try {
						Submission s = testingQueue.take();
						Repo r = sourceManager.getRepo(s.getRepoId());
						Task t = taskManager.getTask(r.getTaskId());
						Result result = new Result(s.getRepoId(),s.getTag());
						s.setResult(result);
						try {
							
							File codeDir = sourceManager.cloneForTesting(s.getRepoId(), s.getTag());
							
							ExecResponse compileResult = ContainerHelper.execCompilation(codeDir, taskManager.getCompileDirectory(t.getTaskId()), t.getImage(), docker);
							result.setCompilationResult(compileResult);
							
							ExecResponse harnessResult = ContainerHelper.execHarness(codeDir,taskManager.getHarnessDirectory(t.getTaskId()),t.getImage(),docker);
							result.setHarnessResult(harnessResult);
														
							ExecResponse validationResult = ContainerHelper.execValidator(taskManager.getValidatorDirectory(t.getTaskId()),harnessResult.getResponse(), t.getImage(),docker);
							result.setValidationResult(validationResult);
							result.insert(database);
							
						} catch (IOException|RepoException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						finally {
							s.setStatus(Submission.STATUS_COMPLETE);
						}
					}
					catch (Throwable e) {
						e.printStackTrace();
					} 
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
