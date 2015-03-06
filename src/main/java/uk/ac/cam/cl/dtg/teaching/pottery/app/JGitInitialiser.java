package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGitInitialiser {

	private static final Logger log =  LoggerFactory.getLogger(JGitInitialiser.class);
		
	public static void init() {
		File t = com.google.common.io.Files.createTempDir();
		try {
			try {
				File empty = new File(t,"empty");
				Git g = Git.init().setDirectory(empty).call();
				try(FileWriter w = new FileWriter(new File(empty,"test.txt"))) {
					w.write("test\n");
				}
				g.add().addFilepattern("test.txt").call();
				g.commit().setMessage("test commit").call();
				g.tag().setName("testTag").call();
				g.getRepository().getTags();
				
				File emptyClone = new File(t,"emptyClone");			
				Git.cloneRepository()
				.setURI("file://"+empty.getPath())
				.setDirectory(emptyClone)
				.setBare(false)
				.setBranch("master")
				.setRemote("origin")
				.call();
			} catch (GitAPIException |IOException e) {
				log.error("Error bootstrapping git library",e);
			}	

			try {
				Git.cloneRepository()
				.setURI("git@github.com:ucam-cl-dtg/empty-repository.git")
				.setDirectory(new File(t,"sshclone"))
				.setBare(false)
				.setBranch("master")
				.setRemote("origin")
				.call();
			} catch (GitAPIException e) {
				log.error("Error bootstrapping git library",e);
			}
		} finally {
			try {
				deleteRecursive(t);
			} catch (IOException e) {
				log.error("Failed to delete temp directory {}",t.getPath(),e);
			}
		}
		log.info("Initialised jGit");
	}
	
	
	/**
	 * Check whether one file is a descendant of the other.  
	 * 
	 * descendant is first converted to its canonical file.  We then walk upwards.  If we find the parent file then we return true.
	 * @param parent
	 * @param descendant
	 * @return true if descendant is a descendant of parent
	 * @throws IOException
	 */
	private static boolean isParent(File parent, File descendant) throws IOException {
		descendant = descendant.getCanonicalFile();
		do {
			if (descendant.equals(parent))
				return true;
		} while ((descendant = descendant.getParentFile()) != null);
		return false;
	}
	
	private static void deleteRecursive(final File dir) throws IOException {
		Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				if (isParent(dir, file.toFile())) {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				} else {
					throw new IOException("File not within parent directory");
				}
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}

		});
	}
}
