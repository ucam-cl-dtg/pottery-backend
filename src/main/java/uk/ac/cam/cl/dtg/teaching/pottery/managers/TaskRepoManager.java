package uk.ac.cam.cl.dtg.teaching.pottery.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import com.google.inject.Inject;

import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerHelper;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.CompilationResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskRepo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskRepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskTestResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.ValidationResponse;

public class TaskRepoManager {


	private DockerApi docker;
	private Config config;

	@Inject
	public TaskRepoManager(Config config, DockerApi docker) {
		this.config = config;
		this.docker = docker;
	}

	public TaskRepo create() throws IOException {
		File taskRepoRoot = config.getTaskRepoRoot();
		String taskRepoId = FileUtil.createNewDirectory(taskRepoRoot);
		File taskRepoDir = new File(taskRepoRoot,taskRepoId);		
		try {
			Git.init().setBare(true).setDirectory(taskRepoDir).call();
		} catch (IllegalStateException|GitAPIException e) {
			IOException toThrow = new IOException("Failed to initialise git repository",e);
			try {
				FileUtil.deleteRecursive(taskRepoDir);
			} catch (IOException e1) {
				e1.addSuppressed(toThrow);
				throw e1;
			}
			throw toThrow;
		}
		return new TaskRepo(taskRepoId);
	}

	public Task register(String taskRepoId, String sha1) throws IOException {
		File taskRepoDir = new File(config.getTaskRepoRoot(),taskRepoId);
		if (!taskRepoDir.exists()) {
			throw new IOException("Failed to open task repository directory");
		}
		try(Git g = Git.open(taskRepoDir)) {
			try(RevWalk revwalk = new RevWalk(g.getRepository())) {
				ObjectId id = g.getRepository().resolve(sha1);
				RevCommit commit = revwalk.parseCommit(id);
				// TODO: this should be unique and not used for any other tasks
				String taskUuid = UUID.randomUUID().toString();
				try {
					g.tag().setObjectId(commit).setName("task:"+taskUuid).call();
					Task t = new Task();
					t.setTaskId(taskUuid);
					return t;
				} catch (GitAPIException e) {
					throw new IOException("Failed to write tag in git repository",e);
				}
			}
		}
	}

	/**
	 * Test to see that this checkout of the task actually conforms to what we need.
	 * 1) make a clone at the required SHA1
	 * 2) check that it compiles
	 * 3) make a submission based on the model answer
	 * 4) run that through a compile, harness, validate loop
	 * @param taskRepoId
	 * @param sha1
	 * @throws IOException 
	 */
	public TaskTestResponse test(String taskRepoId, String sha1) throws IOException, GitAPIException {
		File taskRepoDir = new File(config.getTaskRepoRoot(),taskRepoId);
		if (!taskRepoDir.exists()) {
			throw new IOException("Failed to open task repository directory");
		}
		File tempDir = Files.createTempDirectory(config.getTestingRoot().toPath(),"taskrepotest-").toFile();
		try {
			try (Git g = Git.cloneRepository().setURI(taskRepoDir.getPath()).setDirectory(tempDir).call()) {
				g.reset().setMode(ResetType.HARD).setRef(sha1).call();
				Task t = TaskManager.loadTask(new File(tempDir,"task.json"));
				ExecResponse r = ContainerHelper.execTaskCompilation(tempDir,t.getImage(),docker,config);
				if (!r.isSuccess()) {
					return new TaskTestResponse(false,"Failed to compile test: "+r.getResponse());
				}
				CompilationResponse r2 = ContainerHelper.execCompilation(new File(tempDir,"solution"), new File(tempDir,"compile"),t.getImage(), docker,config);
				if (!r2.isSuccess()) {
					return new TaskTestResponse(false,"Failed to compile solution: "+r2.getFailMessage());
				}
				HarnessResponse r3 = ContainerHelper.execHarness(new File(tempDir,"solution"), new File(tempDir,"harness"), t.getImage(), docker,config);
				if (!r3.isSuccess()) {
					return new TaskTestResponse(false,"Failed to run harness: "+r3.getFailMessage());
				}
				ValidationResponse r4 = ContainerHelper.execValidator(new File(tempDir,"validator"), r3, t.getImage(), docker,config);
				if (!r4.isSuccess()) {
					return new TaskTestResponse(false,"Failed to validate harness results: "+r4.getFailMessage());
				}
			}
		}
		finally {
			FileUtil.deleteRecursive(tempDir);
		}
		return new TaskTestResponse(true,"Passed");
	}
	
	public Collection<TaskRepoInfo> list() {
		File taskRepoRoot = config.getTaskRepoRoot();
		List<TaskRepoInfo> result = new LinkedList<TaskRepoInfo>();
		for(File taskRepo : taskRepoRoot.listFiles()) {
			if (taskRepo.isDirectory()) {
				result.add(new TaskRepoInfo(taskRepo.getName()));
			}
		}
		return result;
	}
	
}
