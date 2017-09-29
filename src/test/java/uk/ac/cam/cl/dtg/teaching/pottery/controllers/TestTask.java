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

package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.database.InMemoryDatabase;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;

public class TestTask {

  private File testRootDir;

  @Before
  public void setup() {
    this.testRootDir = Files.createTempDir();
  }

  @After
  public void after() throws IOException {
    FileUtil.deleteRecursive(testRootDir);
  }

  @Test
  public void getHeadSha_returnsMostRecentCommit()
      throws GitAPIException, SQLException, IOException, TaskStorageException {
    Database database = new InMemoryDatabase();
    TaskConfig taskConfig = new TaskConfig(testRootDir.getPath());
    TaskFactory taskFactory = new TaskFactory(taskConfig, database);
    Task task = taskFactory.createInstance();

    String cloneHeadSha;
    File clone = new File(testRootDir, "clone");
    try (Git g =
        Git.cloneRepository()
            .setURI(task.getTaskDefLocation().toString())
            .setDirectory(clone)
            .call()) {
      Files.write("Test".getBytes(Charsets.UTF_8), new File(clone, "readme.txt"));
      g.add().addFilepattern("readme.txt").call();
      g.commit().setMessage("test commit").call();
      cloneHeadSha = g.getRepository().findRef(Constants.HEAD).getObjectId().getName();
      g.push().call();
    }

    assertThat(task.getHeadSha()).isEqualTo(cloneHeadSha);
  }
}
