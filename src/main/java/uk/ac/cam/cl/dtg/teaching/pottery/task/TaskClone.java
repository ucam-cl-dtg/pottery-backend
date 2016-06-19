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
import uk.ac.cam.cl.dtg.teaching.pottery.FourLevelLock;
import uk.ac.cam.cl.dtg.teaching.pottery.FourLevelLock.AutoCloseableLock;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskCloneException;

public class TaskClone {

	protected static final Logger LOG = LoggerFactory.getLogger(TaskClone.class);

	private File repo;

	private volatile TaskInfo info;

	private final File upstreamRepo;

	/**
	 * Lock controlling access to the repo and files
	 */
	private final FourLevelLock lock = new FourLevelLock();

	/**
	 * Do not create directly. Access through the members of the Task object.
	 * This is because we want to ensure that there is only one instance of this
	 * object for each repo directory so that we can control concurrent access
	 * 
	 * @throws GitAPIException
	 * @throws IOException
	 */
	TaskClone(File upstreamRepo, File repo, String tag) throws TaskCloneException, IOException {
		this.repo = repo;
		this.upstreamRepo = upstreamRepo;
		update(tag, false);
		// this.info is filled in by the update method
	}

	/**
	 * Lookup the SHA1 for the HEAD of the repo
	 * 
	 * @return a string containing the SHA1
	 * @throws TaskCloneException
	 *             if an error occurs trying to read the repo
	 */
	public String getHeadSHA() throws TaskCloneException {
		try (AutoCloseableLock l = lock.takeGitDbOpLock()) {
			try (Git g = Git.open(repo)) {
				Ref r = g.getRepository().findRef(Constants.HEAD);
				return r.getObjectId().getName();
			} catch (IOException e) {
				throw new TaskCloneException("Failed to read Git repository for " + repo, e);
			}
		} catch (InterruptedException e1) {
			throw new TaskCloneException("Interrupted waiting to git operation lock",e1);
		}
	}

	/**
	 * Update the working directory in the repo to the tag given.
	 * 
	 * @param tag
	 *            the tag/sha1 to update the repo to (can be HEAD)
	 * @param fromScratch
	 *            if this is true then the repo will be deleted and cloned from
	 *            scratch. If false then a pull and reset will be tried. If the
	 *            pull and reset fails then this method automatically tries
	 *            again with a full delete and clone
	 * @throws TaskCloneException 
	 * @throws IOException
	 */
	public void update(String tag, boolean fromScratch) throws TaskCloneException, IOException {
		LOG.debug("Updating {} to {}", repo, tag);

		if (fromScratch || !repo.exists()) {
			try (AutoCloseableLock l = lock.takeFullExclusionLock()) {
				FileUtil.deleteRecursive(repo);
				try (Git g = Git.cloneRepository().setURI(upstreamRepo.getPath()).setDirectory(repo).call()) {
					g.reset().setMode(ResetType.HARD).setRef(tag).call();
					info = TaskInfo.load(repo);
					return;
				} catch (GitAPIException e) {
					throw new TaskCloneException("Failed to create clone of " + upstreamRepo + " and reset to " + tag,
							e);
				}
			} catch (InterruptedException e1) {
				throw new TaskCloneException("Interrupted whilst waiting for exclusive lock",e1);
			} 
		} else {
			Exception caught = null;
			try (AutoCloseableLock l = lock.takeFileWritingLock()) {
				try (Git g = Git.open(repo)) {
					PullResult p = g.pull().setRemote("origin").call();
					if (p.isSuccessful()) {
						g.reset().setMode(ResetType.HARD).setRef(tag).call();
					}
				} catch (IOException | GitAPIException e) {
					// if we failed to do the lightweight update then try
					// again with a full delete and clone
					// but don't call it here because we already hold the
					// read lock so we'll deadlock
					caught = e;
				}
				info = TaskInfo.load(repo);
			} catch (InterruptedException e1) {
				throw new TaskCloneException("Interrupted whilst waiting for file writing lock",e1);
			} 

			if (caught != null) {
				try {
					update(tag, true);
				} catch (IOException | TaskCloneException e) {
					e.addSuppressed(caught);
					throw e;
				}
			}
		}
	}

	/**
	 * Copy the skeleton files from this task clone to the target directory given (this will be in a candidates repo)
	 * 
	 * @param destination
	 * @return
	 * @throws IOException
	 */
	public List<String> copySkeleton(File destination) throws IOException {
		try (AutoCloseableLock l = lock.takeFileReadingLock()) {
			File sourceLocation = getSkeletonRoot();
			if (!sourceLocation.exists()) {
				return new LinkedList<>();
			}
			List<String> copiedFiles = new LinkedList<>();
			Files.walkFileTree(sourceLocation.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

					File originalFile = file.toFile();
					Path localLocation = sourceLocation.toPath().relativize(file);
					copiedFiles.add(localLocation.toString());
					File newLocation = destination.toPath().resolve(localLocation).toFile();
					File newDir = newLocation.getParentFile();

					if (newDir.exists()) {
						newDir.mkdirs();
					}

					try (FileOutputStream fos = new FileOutputStream(newLocation)) {
						try (FileInputStream fis = new FileInputStream(originalFile)) {
							IOUtils.copy(fis, fos);
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
			return copiedFiles;
		} catch (InterruptedException e) {
			throw new IOException("Interrupted whilst waiting for file reading lock",e);
		}
	}

	/**
	 * Return the taskinfo object associated with this taskclone
	 * 
	 * (no locking needed here since the field is volatile and TaskInfo objects are immutable)
	 * 
	 * @return
	 */
	public TaskInfo getInfo() {
		return info;
	}

	/**
	 * Move this task clone to another place on disk
	 * 
	 * @param taskRegisteredRoot
	 * @throws IOException
	 */
	public void move(File taskRegisteredRoot) throws IOException {
		try (AutoCloseableLock l = lock.takeFullExclusionLock()) {
			FileUtil.deleteRecursive(taskRegisteredRoot);
			if (!repo.renameTo(taskRegisteredRoot)) {
				throw new IOException("Failed to move clone from " + repo + " to " + taskRegisteredRoot);
			}
			repo = taskRegisteredRoot;
		} catch (InterruptedException e) {
			throw new IOException("Interrupted whilst acquiring exclusive lock",e);
		}
	}

	private File getSkeletonRoot() {
		return new File(repo, "skeleton");
	}

	private File getCompileRoot() {
		return new File(repo, "compile");
	}

	private File getHarnessRoot() {
		return new File(repo, "harness");
	}

	private File getValidatorRoot() {
		return new File(repo, "validator");
	}

	/**
	 * Run the given code in the context of the repos working directory. This code is run with
	 * a read/write lock on the files in the working directory
	 * 
	 * @param t
	 * @throws InterruptedException 
	 */
	public void runInContext(RunnableWithinTask t) throws InterruptedException {
		try (AutoCloseableLock l = lock.takeFileWritingLock()) {
			t.run(getCompileRoot(), getHarnessRoot(), getValidatorRoot());
		} 
	}
}
