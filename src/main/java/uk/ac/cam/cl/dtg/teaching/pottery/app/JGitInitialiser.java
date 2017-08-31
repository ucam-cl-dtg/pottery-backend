/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015 Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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

  protected static final Logger LOG = LoggerFactory.getLogger(JGitInitialiser.class);

  public static void init() {
    File t = com.google.common.io.Files.createTempDir();
    try {
      try {
        File empty = new File(t, "empty");
        Git g = Git.init().setDirectory(empty).call();
        try (FileWriter w = new FileWriter(new File(empty, "test.txt"))) {
          w.write("test\n");
        }
        g.add().addFilepattern("test.txt").call();
        g.commit().setMessage("test commit").call();
        g.tag().setName("testTag").call();
        g.getRepository().getTags();

        File emptyClone = new File(t, "emptyClone");
        Git.cloneRepository()
            .setURI("file://" + empty.getPath())
            .setDirectory(emptyClone)
            .setBare(false)
            .setBranch("master")
            .setRemote("origin")
            .call();
      } catch (GitAPIException | IOException e) {
        LOG.error("Error bootstrapping git library", e);
      }

      try {
        Git.cloneRepository()
            .setURI("git@github.com:ucam-cl-dtg/empty-repository.git")
            .setDirectory(new File(t, "sshclone"))
            .setBare(false)
            .setBranch("master")
            .setRemote("origin")
            .call();
      } catch (GitAPIException e) {
        LOG.error("Error bootstrapping git library", e);
      }
    } finally {
      try {
        FileUtil.deleteRecursive(t);
      } catch (IOException e) {
        LOG.error("Failed to delete temp directory {}", t.getPath(), e);
      }
    }
    LOG.info("Initialised jGit");
  }
}
