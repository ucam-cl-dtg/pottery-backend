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

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionAlreadyScheduledException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class SubmissionsController
    implements uk.ac.cam.cl.dtg.teaching.pottery.api.SubmissionsController {

  protected static final Logger LOG = LoggerFactory.getLogger(SubmissionsController.class);

  private Worker worker;

  private Database database;

  private RepoFactory repoFactory;

  /** Create a new SubmissionController. */
  @Inject
  public SubmissionsController(Worker worker, Database database, RepoFactory repoFactory) {
    super();
    this.worker = worker;
    this.database = database;
    this.repoFactory = repoFactory;
  }

  @Override
  public String scheduleTest(String repoId, String tag)
      throws RepoStorageException, RepoExpiredException, SubmissionStorageException,
          RepoNotFoundException {
    Repo r = repoFactory.getInstance(repoId);
    return r.scheduleSubmission(tag, worker, database);
  }

  @Override
  public String getSubmission(String repoId, String tag)
      throws SubmissionNotFoundException, RepoStorageException, SubmissionStorageException,
          RepoNotFoundException {
    return repoFactory.getInstance(repoId).getSubmission(tag, database);
  }

  @Override
  public String getSubmission(String repoId, String tag, String step)
      throws SubmissionNotFoundException, RepoStorageException, SubmissionStorageException,
      RepoNotFoundException {
    return repoFactory.getInstance(repoId).getSubmissionOutput(tag, step, database);
  }

  @Override
  public Response deleteSubmission(String repoId, String tag)
      throws RepoStorageException, RepoNotFoundException, SubmissionStorageException,
          SubmissionNotFoundException, SubmissionAlreadyScheduledException {
    repoFactory.getInstance(repoId).deleteSubmission(tag, database);
    return Response.ok().build();
  }
}
