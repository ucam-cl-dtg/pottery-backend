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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RetiredTaskException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionAlreadyScheduledException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;

public class TestSubmission {

  private File testRootDir;
  private Repo repo;
  private TestEnvironment testEnvironment;

  /** Configure the test environment. */
  @Before
  public void setup()
      throws IOException, GitAPIException, TaskStorageException, SQLException,
          TaskNotFoundException, CriterionNotFoundException, RetiredTaskException,
          RepoExpiredException, RepoNotFoundException, RepoStorageException {

    this.testRootDir = Files.createTempDir().getCanonicalFile();
    this.testEnvironment = new TestEnvironment(testRootDir.getPath());

    Task task = testEnvironment.createNoOpTask();
    this.repo = testEnvironment.createRepo(task);
  }

  @After
  public void tearDown() throws IOException {
    FileUtil.deleteRecursive(testRootDir);
  }

  @Test
  public void scheduleSubmission_succeeds()
      throws RepoStorageException, RepoExpiredException, SubmissionStorageException,
          SubmissionNotFoundException {
    String tag = repo.createNewTag();
    repo.scheduleSubmission(tag, testEnvironment.getWorker(), testEnvironment.getDatabase());
    Submission submission = repo.getSubmission(tag, testEnvironment.getDatabase());
    assertThat(submission.isComplete()).isTrue();
  }

  @Test
  public void deleteSubmission_succeeds()
      throws RepoStorageException, RepoExpiredException, SubmissionStorageException,
          SubmissionNotFoundException, SubmissionAlreadyScheduledException {
    String tag = repo.createNewTag();
    Database database = testEnvironment.getDatabase();
    repo.scheduleSubmission(tag, testEnvironment.getWorker(), database);
    repo.deleteSubmission(tag, database);
    try {
      repo.getSubmission(tag, database);
      fail("getSubmission should throw SubmissionNotFoundException");
    } catch (SubmissionNotFoundException e) {
      assertThat(e).hasMessageThat().contains(tag);
    }
  }

  @Test
  public void deleteSubmission_failsWhenBlocked()
      throws RepoStorageException, RepoExpiredException, SubmissionStorageException,
          SubmissionNotFoundException, InterruptedException {
    String tag = repo.createNewTag();
    Database database = testEnvironment.getDatabase();
    testEnvironment.getContainerBackend().block();
    Thread scheduleThread =
        new Thread(
            () -> {
              try {
                repo.scheduleSubmission(tag, testEnvironment.getWorker(), database);
              } catch (RepoExpiredException | SubmissionStorageException | RepoStorageException e) {
                throw new RuntimeException(e);
              }
            });
    try {
      scheduleThread.start();
      testEnvironment.getContainerBackend().waitForBlocked();
      try {
        repo.deleteSubmission(tag, database);
        fail("deleteSubmission should throw SubmissionAlreadyScheduledException");
      } catch (SubmissionAlreadyScheduledException e) {
        assertThat(e).hasMessageThat().contains("Submission is still active");
      }
    } finally {
      testEnvironment.getContainerBackend().unblock();
      scheduleThread.join(); // don't teardown the test until we've finished running the submission
    }
  }
}
