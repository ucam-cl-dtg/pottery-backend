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
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerHelper;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.CompilationResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.ValidationResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionAlreadyScheduledException;
import uk.ac.cam.cl.dtg.teaching.pottery.managers.TaskManager;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;

@Singleton
public class Store {

	public static final Logger LOG = LoggerFactory.getLogger(Store.class); 
			
	public LinkedBlockingQueue<Submission> testingQueue = new LinkedBlockingQueue<Submission>();
	
	private AtomicBoolean running = new AtomicBoolean(true);
	
	private Thread worker;
	
	private RepoFactory repoFactory;

	private Database database;

	@Inject
	public Store(RepoFactory repoFactory, final TaskManager taskManager, final DockerApi docker, Database database, final Config config) throws SQLException {
		this.repoFactory = repoFactory;
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
						Repo r = repoFactory.getInstance(s.getRepoId());
						Task t = r.isReleased() ? taskManager.getReleasedTask(r.getTaskId()) : taskManager.getTestingTask(r.getTaskId());
						LOG.info("Testing {},released={},id={}",t.getName(),t.isReleased(),t.getTaskId());
						try {
							
							r.setVersionToTest(s.getTag());
							
							File codeDir = r.getTestingDirectory();
							
							CompilationResponse compilationResponse = ContainerHelper.execCompilation(codeDir, taskManager.getCompileRoot(t), t.getImage(), docker,config);
							s.setCompilationResponse(compilationResponse);
							HarnessResponse harnessResponse = ContainerHelper.execHarness(codeDir,taskManager.getHarnessRoot(t),t.getImage(),docker,config);
							s.setHarnessResponse(harnessResponse);
							ValidationResponse validationResponse = ContainerHelper.execValidator(taskManager.getValidatorRoot(t),harnessResponse, t.getImage(),docker,config);
							s.setValidationResponse(validationResponse);
						} catch (RepoException e) {
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
	
	public synchronized Submission createSubmission(String repoId, String tag) throws RepoException, SubmissionAlreadyScheduledException, IOException, SQLException {

		Repo repo = repoFactory.getInstance(repoId);
		
		try (TransactionQueryRunner q = database.getQueryRunner()) {
			Submission s = Submission.getByRepoIdAndTag(repoId, tag, q);
			if (s != null) {
				throw new SubmissionAlreadyScheduledException();
			}
			
			if (repo.existsTag(tag)) {
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
	
	@PreDestroy
	public void stop() {
		running.set(false);
		worker.interrupt();
	}

}
