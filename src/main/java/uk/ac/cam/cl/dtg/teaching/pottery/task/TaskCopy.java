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

import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TwoPhaseLatch;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;

/**
 * A task copy is a checkout of a task. Its made by copying files out of the
 * taskdef git repo. Task copies are readonly - you can't change the files in
 * them.
 * 
 * @author acr31
 *
 */
public class TaskCopy implements AutoCloseable {
	
	private String copyId;
	private TaskConfig config;
	private TaskInfo info;
	private String sha1;
	
	/**
	 * Instances of this object are created by the TaskCopyBuilder object. Its 
	 * up to the TaskCopyBuilder object to ensure that there is only instance
	 * of this object for each copyId
	 * 
	 * @param taskId the ID of the task itself
	 * @param copyId the unique ID for this copy
	 * @param sha1 the SHA1 of the copy we made from the parent repo
	 * @param config information for tasks
	 * @throws IOException if we can't load the task information from the filesystem
	 */
	TaskCopy(String taskId, String copyId, String sha1, TaskConfig config) throws IOException {
		super();
		this.copyId = copyId;
		this.config = config;
		this.sha1 = sha1;
		this.info = TaskInfo.load(taskId,config.getTaskCopyDir(copyId));
	}

	public String getSha1() {
		return sha1;
	}

	public String getCopyId() {
		return copyId;
	}
	
	public TaskInfo getInfo() {
		return info;
	}

	public File getLocation() {
		return config.getTaskCopyDir(copyId);
	}
	
	/**
	 * Copy the skeleton files from this task copy to the target directory given (this will be in a candidates repo)
	 * 
	 * @param destination
	 * @return
	 * @throws IOException
	 */
	public List<String> copySkeleton(File destination) throws IOException {
		File sourceLocation = config.getSkeletonDir(copyId);
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
	}
	
	public File getCompileRoot() {
		return config.getCompileDir(copyId);
	}

	public File getHarnessRoot() {
		return config.getHarnessDir(copyId);
	}

	public File getValidatorRoot() {
		return config.getValidatorDir(copyId);
	}
	
	private TwoPhaseLatch latch = new TwoPhaseLatch();
	
	public boolean acquire() { return latch.acquire(); }
	public void release() { latch.release(); }
	
	/**
	 * Delete this copy from the disk
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	void destroy() throws IOException, InterruptedException {
		latch.await();
		FileUtil.deleteRecursive(config.getTaskCopyDir(copyId));
	}

	@Override
	public void close() {
		release();
	}
}
