package uk.ac.cam.cl.dtg.teaching.pottery.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;

public class TaskClone {

	protected static final Logger LOG = LoggerFactory.getLogger(TaskClone.class);
	
	private File repo;
	
	private TaskInfo info;
	
	private File upstreamRepo;
	
	/** 
	 * Do not create directly. Access through the members of the Task object
	 * @throws GitAPIException 
	 * @throws IOException 
	 */
	TaskClone(File upstreamRepo, File repo, String tag) throws TaskCloneException, IOException {
		this.repo = repo;
		this.upstreamRepo = upstreamRepo;
		update(tag,false);
		this.info = TaskInfo.load(repo);
	}

	public synchronized String getHeadSHA() throws TaskCloneException {
		try (Git g = Git.open(repo)) {
			Ref r = g.getRepository().findRef(Constants.HEAD);
			return r.getObjectId().getName();
		} catch (IOException e) {
			throw new TaskCloneException("Failed to read Git repository for "+repo,e);	
		}
	}
	
	public synchronized void update(String tag, boolean fromScratch) throws TaskCloneException, IOException {
		LOG.debug("Updating {} to {}",repo,tag);
		if (fromScratch) FileUtil.deleteRecursive(repo);
		
		if (!repo.exists()) {
			try (Git g = Git.cloneRepository().setURI(upstreamRepo.getPath()).setDirectory(repo).call()) {
				g.reset().setMode(ResetType.HARD).setRef(tag).call();
			} catch (GitAPIException e) {
				throw new TaskCloneException("Failed to create clone of "+upstreamRepo+" and reset to "+tag,e);
			}			
		}
		else {
			try (Git g = Git.open(repo)) {
				PullResult p = g.pull().setRemote("origin").call();
				if (p.isSuccessful()) {
					g.reset().setMode(ResetType.HARD).setRef(tag).call();
				}
			} catch (IOException | GitAPIException e) {
				throw new TaskCloneException("Failed to update "+repo+" to tag "+tag,e);
			}
		}
		info = TaskInfo.load(repo);
	}
	
	public synchronized List<String> copySkeleton(File destination) throws IOException {
		File sourceLocation = getSkeletonRoot();
		if (!sourceLocation.exists()) { return new LinkedList<>(); }
		List<String> copiedFiles = new LinkedList<>();
		Files.walkFileTree(sourceLocation.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				
				File originalFile = file.toFile();
				Path localLocation = sourceLocation.toPath().relativize(file);
				copiedFiles.add(localLocation.toString());
				File newLocation = destination.toPath().resolve(localLocation).toFile();
				File newDir = newLocation.getParentFile();

				if (newDir.exists()) {
					newDir.mkdirs();
				}
				
				try(FileOutputStream fos = new FileOutputStream(newLocation)) {
					try(FileInputStream fis = new FileInputStream(originalFile)) {
						IOUtils.copy(fis, fos);
					}
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return copiedFiles;
	}
	
	public TaskInfo getInfo() {
		return info;
	}

	public synchronized void move(File taskRegisteredRoot) throws IOException {
		FileUtil.deleteRecursive(taskRegisteredRoot);
		if (!repo.renameTo(taskRegisteredRoot)) {
			throw new IOException("Failed to move clone from "+repo+" to "+taskRegisteredRoot);
		}
		repo = taskRegisteredRoot;
	}
	
	public File getSkeletonRoot() {
		return new File(repo,"skeleton");
	}

	public File getCompileRoot() {
		return new File(repo,"compile");
	}

	public File getHarnessRoot() {
		return new File(repo,"harness");
	}

	public File getValidatorRoot() {
		return new File(repo,"validator");
	}
}
