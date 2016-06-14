package uk.ac.cam.cl.dtg.teaching.pottery.app;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;

public class JGitInitialiser {

	protected static final Logger LOG =  LoggerFactory.getLogger(JGitInitialiser.class);
		
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
				LOG.error("Error bootstrapping git library",e);
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
				LOG.error("Error bootstrapping git library",e);
			}
		} finally {
			try {
				FileUtil.deleteRecursive(t);
			} catch (IOException e) {
				LOG.error("Failed to delete temp directory {}",t.getPath(),e);
			}
		}
		LOG.info("Initialised jGit");
	}
}
