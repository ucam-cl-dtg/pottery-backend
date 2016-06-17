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
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
	
	private volatile TaskInfo info;
	
	private final File upstreamRepo;
	
	/**
	 * Lock controlling access to the filesystem permitting multiple concurrent readers and exclusive writers
	 * 
	 * If you are using the git database only then you just need a read lock
	 * 
	 * If you are doing something that will destroy the git database (clone, move) then you need the write lock
	 * 
	 * If you are doing something that affects the working directory then you need the read lock as the fs lock below
	 */
	private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	
	/**
	 * Lock providing mutual exclusion on working directory operations. Acquire this last.
	 * You need to hold this even if you are only reading files in the working directory
	 */
	private final Object fileLock = new Object();
	
	/** 
	 * Do not create directly. Access through the members of the Task object. This is because
	 * we want to ensure that there is only one instance of this object for each repo 
	 * directory so that we can control concurrent access
	 * @throws GitAPIException 
	 * @throws IOException 
	 */
	TaskClone(File upstreamRepo, File repo, String tag) throws TaskCloneException, IOException {
		this.repo = repo;
		this.upstreamRepo = upstreamRepo;
		update(tag,false);
		// this.info is filled in by the update method
	}

	public String getHeadSHA() throws TaskCloneException {
		readWriteLock.readLock().lock();;
		try {
			try (Git g = Git.open(repo)) {
				Ref r = g.getRepository().findRef(Constants.HEAD);
				return r.getObjectId().getName();
			} catch (IOException e) {
				throw new TaskCloneException("Failed to read Git repository for "+repo,e);	
			}
		}
		finally {
			readWriteLock.readLock().unlock();
		}
	}
	
	public void update(String tag, boolean fromScratch) throws TaskCloneException, IOException {
		LOG.debug("Updating {} to {}",repo,tag);
		
		if (fromScratch || !repo.exists()) {
			readWriteLock.writeLock().lock();
			try {
				FileUtil.deleteRecursive(repo);
				try (Git g = Git.cloneRepository().setURI(upstreamRepo.getPath()).setDirectory(repo).call()) {
					g.reset().setMode(ResetType.HARD).setRef(tag).call();
					info = TaskInfo.load(repo);
					return;
				} catch (GitAPIException e) {
					throw new TaskCloneException("Failed to create clone of "+upstreamRepo+" and reset to "+tag,e);
				}
			}
			finally {
				readWriteLock.writeLock().unlock();
			}
		}
		else {
			Exception caught = null;
			readWriteLock.readLock().lock();
			try {
				synchronized (fileLock) {
					try (Git g = Git.open(repo)) {
						PullResult p = g.pull().setRemote("origin").call();
						if (p.isSuccessful()) {
							g.reset().setMode(ResetType.HARD).setRef(tag).call();
						}
					} catch (IOException | GitAPIException e) {
						// if we failed to do the lightweight update then try again with a full delete and clone
						// but don't call it here because we already hold the read lock so we'll deadlock
						caught = e;
					}
				}
				info = TaskInfo.load(repo);
			}
			finally {
				readWriteLock.readLock().unlock();
			}
			
			if (caught != null) {
				try {
					update(tag,true);
				}
				catch (IOException | TaskCloneException e) {
					e.addSuppressed(caught);
					throw e;
				}
			}
		}
	}
	
	public List<String> copySkeleton(File destination) throws IOException {
		readWriteLock.readLock().lock();
		try {
			synchronized (fileLock) {
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
		}
		finally {
			readWriteLock.readLock().unlock();
		}
	}
	
	public TaskInfo getInfo() {
		return info;
	}

	public void move(File taskRegisteredRoot) throws IOException {
		readWriteLock.writeLock().lock();
		try {
			FileUtil.deleteRecursive(taskRegisteredRoot);
			if (!repo.renameTo(taskRegisteredRoot)) {
				throw new IOException("Failed to move clone from "+repo+" to "+taskRegisteredRoot);
			}
			repo = taskRegisteredRoot;
		}
		finally {
			readWriteLock.writeLock().unlock();
		}
	}
	
	private File getSkeletonRoot() {
		return new File(repo,"skeleton");
	}

	private File getCompileRoot() {
		return new File(repo,"compile");
	}

	private File getHarnessRoot() {
		return new File(repo,"harness");
	}

	private File getValidatorRoot() {
		return new File(repo,"validator");
	}
	
	public void runInContext(RunnableWithinTask t) {
		readWriteLock.readLock().lock();
		try {
			synchronized (fileLock) {
				t.run(getCompileRoot(), getHarnessRoot(), getValidatorRoot());
			}
		}
		finally {
			readWriteLock.readLock().unlock();
		}
	}
	
}
