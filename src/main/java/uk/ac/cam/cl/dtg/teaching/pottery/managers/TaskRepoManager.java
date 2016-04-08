package uk.ac.cam.cl.dtg.teaching.pottery.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import com.google.inject.Inject;

import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.UUIDGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.app.RegistrationTag;
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

	private UUIDGenerator uuidGenerator;
	
	@Inject
	public TaskRepoManager(Config config, DockerApi docker) {
		this.config = config;
		this.docker = docker;
		this.uuidGenerator = new UUIDGenerator();
		
		File taskRepoRoot = config.getTaskRepoRoot();
		for(File f : taskRepoRoot.listFiles()) {
			if (f.getName().startsWith(".")) continue;
			uuidGenerator.reserve(f.getName());
		}
	}

	public TaskRepo create() throws IOException {
		File taskRepoRoot = config.getTaskRepoRoot();
		String taskRepoId = uuidGenerator.generate();
		File taskRepoDir = new File(taskRepoRoot,taskRepoId);
		taskRepoDir.mkdir();
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

	public RegistrationTag recordRegistration(String taskRepoId, String sha1, String newUuid, boolean active) throws IOException, GitAPIException {
		File taskRepoDir = new File(config.getTaskRepoRoot(),taskRepoId);
		RegistrationTag tag = new RegistrationTag(active, newUuid,taskRepoDir);
		String tagName = tag.toTagName();
		
		if (!taskRepoDir.exists()) {
			throw new IOException("Failed to open task repository directory");
		}
		try(Git g = Git.open(taskRepoDir)) {
			try(RevWalk revwalk = new RevWalk(g.getRepository())) {
				ObjectId id = g.getRepository().resolve(sha1);
				RevCommit commit = revwalk.parseCommit(id);
				File taskDir = new File(config.getRegisteredTaskRoot(),newUuid);
				try {
					g.tag().setObjectId(commit).setName(tagName).call();
				} catch (GitAPIException e) {
					FileUtil.deleteRecursive(taskDir);
					throw e;
				}
			}
		}
		return tag;
	}

	public void removeRegistration(RegistrationTag tag) throws IOException, GitAPIException {
		File taskRepoDir = tag.getRepository();
		if (!taskRepoDir.exists()) {
			throw new IOException("Failed to open task repository directory");
		}
		try(Git g = Git.open(taskRepoDir)) {
			g.tagDelete().setTags(tag.toTagName()).call();
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
				Task t = TaskManager.loadTask(tempDir.getName(),false,new File(tempDir,"task.json"));
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
