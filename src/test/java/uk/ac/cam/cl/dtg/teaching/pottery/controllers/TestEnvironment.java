/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 Andrew Rice (acr31@cam.ac.uk), BlueOptima Limited
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.config.TaskConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.UncontainerImpl;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Execution;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Step;
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

class TestEnvironment {

  public static final String VARIANT = "shell";

  private final String testRootDir;
  private final TaskFactory taskFactory;
  private final RepoFactory repoFactory;
  private final TaskIndex taskIndex;
  private final Worker worker;
  private final RepoConfig repoConfig;
  private final UncontainerImpl containerBackend;
  private final Database database;

  TestEnvironment(String testRootDir)
      throws GitAPIException, SQLException, IOException, TaskStorageException {
    this.testRootDir = testRootDir;
    this.database = new InMemoryDatabase();
    TaskConfig taskConfig = new TaskConfig(testRootDir);
    this.taskFactory = new TaskFactory(taskConfig, database);
    this.repoConfig = new RepoConfig(testRootDir);
    this.repoFactory = new RepoFactory(repoConfig, database);
    this.taskIndex = new TaskIndex(taskFactory, database);
    ContainerEnvConfig containerEnvConfig = new ContainerEnvConfig(testRootDir);
    this.containerBackend = new UncontainerImpl();
    ContainerManager containerManager = new ContainerManager(containerEnvConfig, containerBackend);
    this.worker = new BlockingWorker(taskIndex, repoFactory, containerManager, database);
  }

  RepoConfig getRepoConfig() {
    return repoConfig;
  }

  Worker getWorker() {
    return worker;
  }

  Database getDatabase() {
    return database;
  }

  UncontainerImpl getContainerBackend() {
    return containerBackend;
  }

  Repo createRepo(Task task)
      throws RepoStorageException, RepoNotFoundException, TaskNotFoundException,
          RepoExpiredException {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.YEAR, 10);
    try (TaskCopy c = task.acquireTestingCopy()) {
      Repo repo =
          repoFactory.createInstance(task.getTaskId(), true, c.getTaskCommit(),
              calendar.getTime(), VARIANT, RepoInfo.REMOTE_UNSET);
      repo.copyFiles(c);
      return repo;
    }
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

      makeScript(copyRoot, "compile-test.sh", printingScript("Compiling test"), g);

      makeScript(copyRoot, "steps/compile/" + VARIANT + "/compile-solution.sh", printingScript("Compiling solution"), g);

      makeScript(copyRoot, "steps/harness/" + VARIANT + "/run-harness.sh", printingScript("Harness output"), g);

      makeScript(copyRoot, "skeleton/" + VARIANT + "/skeleton.sh", printingScript("Skeleton"), g);

      makeScript(copyRoot, "output.sh", argListingScript(), g);

      Map<String, String> variantSolutionMap = new HashMap<>();
      variantSolutionMap.put("success", null);
      TaskInfo i =
          new TaskInfo(
              TaskInfo.TYPE_ALGORITHM,
              "Empty task",
              ImmutableSet.of("correctness"),
              "easy",
              0,
              "Empty task",
              ImmutableList.of(),
              ImmutableSet.of(VARIANT),
              Map.of(VARIANT, variantSolutionMap),
              List.of(new Execution("template:java", "@TASK@/compile-test.sh", null)),
              List.of(
                new Step("compile", Map.of(VARIANT, new Execution("template:java", "@STEP@/compile-solution.sh", null))),
                new Step("harness", Map.of(VARIANT, new Execution("template:java", "@STEP@/run-harness.sh", null))),
                new Step("validate", Map.of("default", new Execution("template:java", "@SHARED@/run-validator.sh", null)))
              ),
              Map.of(VARIANT, new Execution("template:java", "@TASK@/output.sh", null))
              );
      TaskInfos.save(i, copyRoot);
      g.add().addFilepattern("task.json").call();

      makeScript(copyRoot, "steps/validate/shared/run-validator.sh", printingScript("Validator output"), g);

      g.commit().setMessage("Empty task").call();
      g.push().call();
    }
    taskIndex.add(task);
    task.scheduleBuildTestingCopy(worker);
    return task;
  }

  static String printingScript(Object toPrint) throws JsonProcessingException {
    return ImmutableList.of(
            "#!/bin/bash",
            "",
            "cat <<EOF",
            new ObjectMapper().writer().writeValueAsString(toPrint),
            "EOF")
        .stream()
        .collect(Collectors.joining("\n"));
  }

  static String argListingScript() {
    return ImmutableList.of(
        "#!/bin/bash",
        "",
        "echo $@")
        .stream()
        .collect(Collectors.joining("\n"));
  }

  private static void makeScript(File root, String fileName, String scriptContents, Git git)
      throws IOException, GitAPIException {
    File file = new File(root, fileName);
    if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
      throw new IOException("Failed to create " + file.getParent());
    }
    try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
      w.print(scriptContents);
    }
    if (!file.setExecutable(true)) {
      throw new IOException("Failed to chmod " + fileName);
    }
    git.add().addFilepattern(fileName).call();
  }

}
