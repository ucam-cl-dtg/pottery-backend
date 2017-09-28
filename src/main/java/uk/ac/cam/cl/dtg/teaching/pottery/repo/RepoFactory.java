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
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;

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

  @Inject
  public RepoFactory(RepoConfig config, Database database) throws IOException {
    this.database = database;
    this.config = config;
    FileUtil.mkdir(config.getRepoRoot());
    FileUtil.mkdir(config.getRepoTestingRoot());
    for (File f : config.getRepoRoot().listFiles()) {
      if (f.getName().startsWith(".")) {
        continue;
      }
      String uuid = f.getName();
      uuidGenerator.reserve(uuid);
    }
  }

  public Repo getInstance(String repoId) throws RepoStorageException, RepoNotFoundException {
    try {
      return cache.get(repoId);
    } catch (ExecutionException e) {
      rethrowExecutionException(e);
      throw new Error(e);
    }
  }

  public Repo createInstance(String taskId, boolean usingTestingVersion, Date expiryDate)
      throws RepoStorageException, RepoNotFoundException {
    final String newRepoId = uuidGenerator.generate();
    try {
      return cache.get(
          newRepoId,
          () ->
              Repo.createRepo(
                  newRepoId, taskId, usingTestingVersion, expiryDate, config, database));
    } catch (ExecutionException e) {
      rethrowExecutionException(e);
      throw new Error(e);
    }
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
