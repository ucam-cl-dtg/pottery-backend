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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.UuidGenerator;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidParameterisationException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskInvalidParametersException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Parameterisation;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Parameterisations;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;

@Singleton
public class RepoFactory {

  /** This object is used to generate new uuids for repos. */
  private UuidGenerator uuidGenerator = new UuidGenerator();

  private ObjectMapper objectMapper = new ObjectMapper();

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
    try {
      return cache.get(repoId);
    } catch (ExecutionException e) {
      rethrowExecutionException(e, RepoStorageException.class);
      rethrowExecutionException(e, RepoNotFoundException.class);
      throw new Error(e);
    }
  }

  /** Create a new repo for this task and return it. */
  public Repo createInstance(TaskCopy taskCopy, boolean usingTestingVersion, Date expiryDate,
                             String variant, String remote, int seed, String extraParametersString)
      throws RepoStorageException, RepoNotFoundException, TaskInvalidParametersException,
      InvalidParameterisationException {
    final String newRepoId = uuidGenerator.generate();
    try {
      return cache.get(
          newRepoId,
          () -> {
            JsonNode extraParameters = objectMapper.readTree(extraParametersString);
            if (extraParameters.isNull()) {
              extraParameters = objectMapper.createObjectNode();
            }
            if (!extraParameters.isObject()) {
              throw new TaskInvalidParametersException();
            }
            ObjectNode parameters = (ObjectNode) extraParameters;
            Optional<Parameterisation> parameterisation = taskCopy.getInfo().getParameterisation();
            int mutation;
            if (parameterisation.isPresent()) {
              Parameterisation p = parameterisation.get();
              mutation = seed % Parameterisations.getCount(p);
              parameters.putAll(Parameterisations.generateParameters(p, mutation));
            } else {
              mutation = -1;
            }

            return Repo.createRepo(new RepoInfo(newRepoId, taskCopy.getInfo().getTaskId(),
                    usingTestingVersion, expiryDate, variant, remote, mutation, parameters),
                config,
                database);
          });
    } catch (ExecutionException e) {
      rethrowExecutionException(e, RepoStorageException.class);
      rethrowExecutionException(e, RepoNotFoundException.class);
      rethrowExecutionException(e, TaskInvalidParametersException.class);
      rethrowExecutionException(e, InvalidParameterisationException.class);
      throw new Error(e);
    }
  }

  private <T extends Exception> void rethrowExecutionException(ExecutionException e, Class<T> klass)
      throws T {
    if (klass.isAssignableFrom(e.getCause().getClass())) {
      throw (T) e.getCause();
    }
  }
}
