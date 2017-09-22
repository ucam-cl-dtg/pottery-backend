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

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoTagNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@Produces("application/json")
@Path("/submissions")
@Api(value = "/submissions", description = "Manages requests for testing", position = 2)
public class SubmissionsController implements uk.ac.cam.cl.dtg.teaching.pottery.api.SubmissionsController {

  protected static final Logger LOG = LoggerFactory.getLogger(SubmissionsController.class);

  private Worker worker;

  private Database database;

  private RepoFactory repoFactory;

  @Inject
  public SubmissionsController(Worker worker, Database database, RepoFactory repoFactory) {
    super();
    this.worker = worker;
    this.database = database;
    this.repoFactory = repoFactory;
  }

  @Override
  @POST
  @Path("/{repoId}/{tag}")
  @ApiOperation(
    value = "Schedules a test by creating a submission",
    notes = "A submission is created from a tag in the code repository used by the candidate.",
    position = 0
  )
  public Submission scheduleTest(@PathParam("repoId") String repoId, @PathParam("tag") String tag)
      throws SubmissionNotFoundException, RepoStorageException, RepoExpiredException,
          SubmissionStorageException, RepoNotFoundException, RepoTagNotFoundException {
    Repo r = repoFactory.getInstance(repoId);
    return r.scheduleSubmission(tag, worker, database);
  }

  @Override
  @GET
  @Path("/{repoId}/{tag}")
  @ApiOperation(
    value = "Poll the submission information",
    notes = "Use this call to poll for the results of testing.",
    position = 1
  )
  public Submission getSubmission(@PathParam("repoId") String repoId, @PathParam("tag") String tag)
      throws SubmissionNotFoundException, RepoStorageException, SubmissionStorageException,
          RepoNotFoundException {
    return repoFactory.getInstance(repoId).getSubmission(tag, database);
  }
}
