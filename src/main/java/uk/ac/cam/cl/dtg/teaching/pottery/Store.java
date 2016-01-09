package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerHelper;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionAlreadyScheduledException;

@Singleton
public class Store {

	private static final Logger LOG = LoggerFactory.getLogger(Store.class); 
			
	public LinkedBlockingQueue<Submission> testingQueue = new LinkedBlockingQueue<Submission>();
	
	private AtomicBoolean running = new AtomicBoolean(true);
	
	private Thread worker;
	
	private SourceManager sourceManager;

	private Database database;

	@Inject
	public Store(SourceManager sourceManager, final TaskManager taskManager, final DockerApi docker, Database database) throws SQLException {
		this.sourceManager = sourceManager;
		this.database = database;
		
		try (TransactionQueryRunner r = database.getQueryRunner()) {
			testingQueue.addAll(Submission.getPending(r));
		}
				
		worker = new Thread() {
			@Override
			public void run() {
				while(running.get()) {
					try {
						Submission s = testingQueue.take();
						Repo r = sourceManager.getRepo(s.getRepoId());
						Task t = taskManager.getTask(r.getTaskId());
						try {
							
							File codeDir = sourceManager.cloneForTesting(s.getRepoId(), s.getTag());
							
							ExecResponse compileResult = ContainerHelper.execCompilation(codeDir, taskManager.getCompileDirectory(t.getTaskId()), t.getImage(), docker);
							s.setCompilationSuccess(compileResult.isSuccess());
							s.setCompilationResponse(compileResult.getResponse());
							ExecResponse harnessResult = ContainerHelper.execHarness(codeDir,taskManager.getHarnessDirectory(t.getTaskId()),t.getImage(),docker);
							s.setHarnessSuccess(harnessResult.isSuccess());;
							s.setHarnessResponse(harnessResult.getResponse());
							ExecResponse validationResult = ContainerHelper.execValidator(taskManager.getValidatorDirectory(t.getTaskId()),harnessResult.getResponse(), t.getImage(),docker);
							s.setValidationSuccess(validationResult.isSuccess());
							s.setValidationResponse(validationResult.getResponse());
						} catch (IOException|RepoException e) {
							LOG.error("Caught exception",e);
						}
						finally {
							s.setStatus(Submission.STATUS_COMPLETE);
							LOG.error("Saving {}:{}",s.getRepoId(),s.getTag());
							
							try (TransactionQueryRunner q = database.getQueryRunner()) {
								s.update(q);
								q.commit();
							}
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
	
	public Submission createSubmission(String repoId, String tag) throws RepoException, SubmissionAlreadyScheduledException, IOException, SQLException {
		synchronized(sourceManager.getMutex("repoId")) {
			try (TransactionQueryRunner q = database.getQueryRunner()) {
				Submission s = Submission.getByRepoIdAndTag(repoId, tag, q);
				if (s != null) {
					throw new SubmissionAlreadyScheduledException();
				}
			
				if (sourceManager.existsTag(repoId, tag)) {
					s = new Submission(repoId,tag);
					s.insert(q);
					q.commit();
					testingQueue.add(s);						
					return s;
				}
				else {
					throw new RepoException("Tag not found");
				}
			}
		}
	}
	
	@PreDestroy
	public void stop() {
		running.set(false);
		worker.interrupt();
	}

}
