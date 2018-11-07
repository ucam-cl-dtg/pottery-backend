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
package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.UuidGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfoWithStatus;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;

@Singleton
public class RepoFactory {

  /** This object is used to generate new uuids for repos. */
  private UuidGenerator uuidGenerator = new UuidGenerator();

  private Database database;
  private RepoConfig config;
  // We need to ensure that only one Repo object exists for any repoId so that
  // we guarantee mutual exclusion on the filesystem operations. So we cache created objects
  // here.
  private LoadingCache<String, Repo> cache =
      CacheBuilder.newBuilder()
          .softValues()
          .build(
              new CacheLoader<String, Repo>() {
                @Override
                public Repo load(String key) throws Exception {
                  return Repo.openRepo(key, config, database);
                }
              });

  /** Construct a new RepoFactory object. */
  @Inject
  public RepoFactory(RepoConfig config, Database database) throws IOException {
    this.database = database;
    this.config = config;
    FileUtil.mkdirIfNotExists(config.getRepoRoot());
    FileUtil.mkdirIfNotExists(config.getRepoTestingRoot());
    for (File f : config.getRepoRoot().listFiles()) {
      if (f.getName().startsWith(".")) {
        continue;
      }
      String uuid = f.getName();
      uuidGenerator.reserve(uuid);
    }
  }

  /** Lookup a repo by its repoId. */
  public Repo getInstance(String repoId) throws RepoStorageException, RepoNotFoundException {
    return getInstance(repoId, false);
  }

  public Repo getInstance(String repoId, boolean includeCreating)
      throws RepoStorageException, RepoNotFoundException {
    try {
      Repo instance = cache.get(repoId);
      if (instance.isReady()) {
        return instance;
      } else {
        if (includeCreating) {
          return instance;
        } else {
          throw new RepoNotFoundException("Repository is still being created.");
        }
      }
    } catch (ExecutionException e) {
      rethrowExecutionException(e);
      throw new Error(e);
    }
  }

  /** Create a new repo for this task and return it. */
  public Repo createInstance(
      String taskId, boolean usingTestingVersion, Date expiryDate, String variant, String remote)
      throws RepoStorageException, RepoNotFoundException {
    final String newRepoId = uuidGenerator.generate();
    try {
      return cache.get(
          newRepoId,
          () ->
              Repo.createRepo(
                  new RepoInfo(newRepoId, taskId, usingTestingVersion, expiryDate, variant, remote,
                      null),
                  config,
                  database));
    } catch (ExecutionException e) {
      rethrowExecutionException(e);
      throw new Error(e);
    }
  }

  public void initialiseInstance(String repoId, TaskCopy c, int validityMinutes)
      throws RepoStorageException, RepoExpiredException, RepoNotFoundException {
    Repo repo = getInstance(repoId, true);
    RepoInfo info = repo.toRepoInfo();
    // TODO: Handle remote repositories by cloning the remote repo, write the files in and then
    // pushing it back, and bork if we get a merge conflict.
    if (!info.isRemote()) {
      repo.copyFiles(c);
    }
    repo.markReady(database, validityMinutes);
  }

  private void rethrowExecutionException(ExecutionException e)
      throws RepoStorageException, RepoNotFoundException {
    if (e.getCause() instanceof RepoStorageException) {
      throw (RepoStorageException) e.getCause();
    } else if (e.getCause() instanceof RepoNotFoundException) {
      throw (RepoNotFoundException) e.getCause();
    }
  }
}
