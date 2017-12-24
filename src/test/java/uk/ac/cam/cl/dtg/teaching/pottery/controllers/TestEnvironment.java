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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.DockerContainerImpl;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.database.InMemoryDatabase;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.ContainerRestrictions;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Criterion;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.model.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskInfos;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.BlockingWorker;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessPart;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Measurement;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.ValidatorResponse;

public class TestEnvironment {

  private final String testRootDir;
  private final Database database;
  private final TaskConfig taskConfig;
  private final TaskFactory taskFactory;
  private final ContainerManager containerManager;
  private final RepoFactory repoFactory;
  private final TaskIndex taskIndex;
  private final Worker worker;
  private final RepoConfig repoConfig;

  public TestEnvironment(String testRootDir)
      throws GitAPIException, SQLException, IOException, ApiUnavailableException,
          TaskStorageException {
    this.testRootDir = testRootDir;
    this.database = new InMemoryDatabase();
    this.taskConfig = new TaskConfig(testRootDir);
    this.taskFactory = new TaskFactory(taskConfig, database);
    ContainerEnvConfig containerEnvConfig = new ContainerEnvConfig(testRootDir);
    this.containerManager =
        new ContainerManager(containerEnvConfig, new DockerContainerImpl(containerEnvConfig));
    this.repoConfig = new RepoConfig(testRootDir);
    this.repoFactory = new RepoFactory(repoConfig, database);
    this.taskIndex = new TaskIndex(taskFactory, database);
    this.worker = new BlockingWorker(taskIndex, repoFactory, containerManager, database);
  }

  RepoConfig getRepoConfig() {
    return repoConfig;
  }

  Repo createRepo(Task task)
      throws RepoStorageException, RepoNotFoundException, TaskNotFoundException,
          RepoExpiredException {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.YEAR, 10);
    Repo repo =
        repoFactory.createInstance(
            task.getTaskId(), true, calendar.getTime(), RepoInfo.REMOTE_UNSET);
    try (TaskCopy c = task.acquireTestingCopy()) {
      repo.copyFiles(c);
    }
    return repo;
  }

  Task createNoOpTask()
      throws TaskStorageException, IOException, GitAPIException, CriterionNotFoundException,
          RetiredTaskException {
    Task task = taskFactory.createInstance();
    String taskId = task.getTaskId();

    File copyRoot = new File(testRootDir, taskId + "-clone");
    if (!copyRoot.mkdirs()) {
      throw new IOException("Failed to create " + copyRoot);
    }

    try (Git g =
        Git.cloneRepository()
            .setURI(task.getTaskDefLocation().toString())
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
      TaskInfos.save(i, copyRoot);
      g.add().addFilepattern("task.json").call();

      mkJsonPrintingScript(
          copyRoot,
          "validator/run-validator.sh",
          new ValidatorResponse(true, null, ImmutableList.of(), null),
          g);

      g.commit().setMessage("Empty task").call();
      g.push().call();
    }
    taskIndex.add(task);
    task.scheduleBuildTestingCopy(worker);
    return task;
  }

  static String getScriptContents(Object toPrint) throws JsonProcessingException {
    return ImmutableList.of(
            "#!/bin/bash",
            "",
            "cat <<EOF",
            new ObjectMapper().writer().writeValueAsString(toPrint),
            "EOF")
        .stream()
        .collect(Collectors.joining("\n"));
  }

  private static void mkJsonPrintingScript(File root, String fileName, Object toPrint, Git git)
      throws IOException, GitAPIException {
    File file = new File(root, fileName);
    if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
      throw new IOException("Failed to create " + file.getParent());
    }
    try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
      w.print(getScriptContents(toPrint));
    }
    if (!file.setExecutable(true)) {
      throw new IOException("Failed to chmod " + fileName);
    }
    git.add().addFilepattern(fileName).call();
  }
}
