/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.apache.commons.io.Charsets;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.*;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;

public class TestRepo {

  private File testRootDir;
  private Repo repo;
  private TestEnvironment testEnvironment;
  private Task task;

  /** Configure the test environment. */
  @Before
  public void setup()
      throws IOException, GitAPIException, TaskStorageException, SQLException,
      TaskNotFoundException, CriterionNotFoundException, RetiredTaskException,
      RepoExpiredException, RepoNotFoundException, RepoStorageException,
      TaskInvalidParametersException, InvalidParameterisationException {

    this.testRootDir = Files.createTempDir().getCanonicalFile();
    this.testEnvironment = new TestEnvironment(testRootDir.getPath());

    this.task = testEnvironment.createNoOpTask();
    this.repo = testEnvironment.createRepo(task);
  }

  @After
  public void tearDown() throws IOException {
    FileUtil.deleteRecursive(testRootDir);
  }

  @Test
  public void readFile_getsCorrectContents()
      throws RepoStorageException, RepoFileNotFoundException, RepoTagNotFoundException,
          JsonProcessingException {

    // ARRANGE
    String expectedContents = TestEnvironment.printingScript("Skeleton");

    // ACT
    byte[] fileContents = repo.readFile("HEAD", "skeleton.sh");

    // ASSERT
    assertThat(new String(fileContents)).isEqualTo(expectedContents);
  }

  @Test
  public void listFiles_findsSkeletonFile() throws RepoStorageException, RepoTagNotFoundException {

    // ARRANGE
    ImmutableList<String> expectedFiles = ImmutableList.of("skeleton.sh");

    // ACT
    List<String> files = repo.listFiles("HEAD");

    // ASSERT
    assertThat(files).containsExactlyElementsIn(expectedFiles);
  }

  @Test
  public void createTag_showsUpInListTags() throws RepoStorageException, RepoExpiredException {

    // ACT
    String tagName = repo.createNewTag();
    List<String> tags = repo.listTags();

    // ASSERT
    assertThat(tags).contains(tagName);
  }

  @Test
  public void updateFile_altersFileContents()
      throws RepoExpiredException, RepoFileNotFoundException, RepoStorageException,
          RepoTagNotFoundException {

    // ARRANGE
    byte[] updatedContents = "NEW CONTENTS".getBytes(Charsets.UTF_8);

    // ACT
    repo.updateFile("skeleton.sh", updatedContents);
    byte[] readContents = repo.readFile("HEAD", "skeleton.sh");

    // ASSERT
    assertThat(readContents).isEqualTo(updatedContents);
  }

  @Test
  public void resolveHeadSha_findsCorrectValue()
      throws IOException, GitAPIException, RepoStorageException {

    // ARRANGE
    String headSha;
    File repoDir = testEnvironment.getRepoConfig().getRepoDir(repo.getRepoId());
    try (Git g = Git.open(repoDir)) {
      Files.write(new byte[] {0}, new File(repoDir, "text.txt"));
      g.add().addFilepattern("test.txt").call();
      RevCommit commit = g.commit().setMessage("test commit").call();
      headSha = commit.getName();
    }

    // ACT
    String foundSha = repo.resolveHeadSha();

    // ASSERT
    assertThat(foundSha).isEqualTo(headSha);
  }
}
