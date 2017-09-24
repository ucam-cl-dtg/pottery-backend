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
