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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.LinkedList;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.Criterion;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerRestrictions;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.database.InMemoryDatabase;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoFileNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoTagNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.BlockingWorker;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessPart;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Measurement;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.ValidatorResponse;

public class TestRepoController {

  private RepoFactory repoFactory;
  private RepoController repoController;
  private Database database;
  private File testRootDir;
  private String taskId;

  private static void mkJsonPrintingScript(File root, String fileName, Object toPrint, Git git)
      throws IOException, GitAPIException {
    File file = new File(root, fileName);
    if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
      throw new IOException("Failed to create " + file.getParent());
    }
    ObjectMapper mapper = new ObjectMapper();
    ObjectWriter writer = mapper.writer();
    String string = writer.writeValueAsString(toPrint);
    try (PrintWriter w = new PrintWriter(new FileWriter(file))) {

      for (String a : ImmutableList.of("#!/bin/bash", "", "cat <<EOF", string, "EOF")) {
        w.println(a);
      }
    }
    if (!file.setExecutable(true)) {
      throw new IOException("Failed to chmod " + fileName);
    }
    git.add().addFilepattern(fileName).call();
  }

  /** Configure the test environment. */
  @Before
  public void setup()
      throws IOException, GitAPIException, TaskStorageException, SQLException,
          TaskNotFoundException, CriterionNotFoundException, ApiUnavailableException,
          RetiredTaskException {

    this.testRootDir = Files.createTempDir();
    this.database = new InMemoryDatabase();
    this.repoFactory = new RepoFactory(new RepoConfig(new File(testRootDir, "repos")), database);

    TaskConfig taskConfig = new TaskConfig(new File(testRootDir, "tasks"));
    TaskFactory taskFactory = new TaskFactory(taskConfig, database);
    Task task = taskFactory.createInstance();
    this.taskId = task.getTaskId();

    File copyRoot = new File(testRootDir, taskId + "-clone");
    if (!copyRoot.mkdirs()) {
      throw new IOException("Failed to create " + copyRoot);
    }

    try (Git g =
        Git.cloneRepository()
            .setURI("file://" + taskConfig.getTaskDefinitionDir(taskId).getPath())
            .setDirectory(copyRoot)
            .call()) {

      mkJsonPrintingScript(copyRoot, "compile-test.sh", "Compiling test", g);

      mkJsonPrintingScript(copyRoot, "compile/compile-solution.sh", "Compiling solution", g);

      mkJsonPrintingScript(
          copyRoot,
          "harness/run-harness.sh",
          new HarnessResponse(
              new LinkedList<>(
                  ImmutableList.of(
                      new HarnessPart(
                          "A no-op task",
                          ImmutableList.of("Doing nothing"),
                          ImmutableList.of(new Measurement("correctness", "true", "id")),
                          null,
                          null))),
              true),
          g);

      mkJsonPrintingScript(copyRoot, "skeleton/skeleton.sh", "Skeleton", g);

      TaskInfo i =
          new TaskInfo(
              TaskInfo.TYPE_ALGORITHM,
              "Empty task",
              ImmutableSet.of(new Criterion("correctness")),
              "template:java",
              "easy",
              0,
              "java",
              "Empty task",
              ContainerRestrictions.candidateRestriction(null),
              ContainerRestrictions.candidateRestriction(null),
              ContainerRestrictions.candidateRestriction(null),
              ContainerRestrictions.authorRestriction(null),
              ImmutableList.of());
      i.save(copyRoot);
      g.add().addFilepattern("task.json").call();

      mkJsonPrintingScript(
          copyRoot,
          "validator/run-validator.sh",
          new ValidatorResponse(true, null, ImmutableList.of(), null),
          g);

      g.commit().setMessage("Empty task").call();
      g.push().call();
    }

    ContainerManager containerManager = new ContainerManager(new ContainerEnvConfig());

    TaskIndex taskIndex = new TaskIndex(taskFactory, database);
    taskIndex.add(task);
    Worker w = new BlockingWorker(taskIndex, repoFactory, containerManager, database);
    task.scheduleBuildTestingCopy(w);

    repoController = new RepoController(repoFactory, taskIndex);
  }

  @After
  public void tearDown() throws IOException {
    database.stop();
    FileUtil.deleteRecursive(testRootDir);
  }

  @Test
  public void makeRepo_createsValidRepo()
      throws TaskNotFoundException, RepoExpiredException, RepoNotFoundException,
          RetiredTaskException, RepoStorageException, TaskStorageException,
          RepoFileNotFoundException, RepoTagNotFoundException {
    RepoInfo info = repoController.makeRepo(taskId, true, 60);
    Repo r = repoFactory.getInstance(info.getRepoId());
    String contents = new String(r.readFile("HEAD", "skeleton.sh"));
    assertThat(contents).isNotEmpty();
  }
}
