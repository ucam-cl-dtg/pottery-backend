package uk.ac.cam.cl.dtg.teaching.pottery.api;

import com.wordnik.swagger.annotations.ApiOperation;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.*;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

public interface SubmissionsController {
    @POST
    @Path("/{repoId}/{tag}")
    @ApiOperation(
      value = "Schedules a test by creating a submission",
      notes = "A submission is created from a tag in the code repository used by the candidate.",
      position = 0
    )
    Submission scheduleTest(@PathParam("repoId") String repoId, @PathParam("tag") String tag)
        throws SubmissionNotFoundException, RepoStorageException, RepoExpiredException,
            SubmissionStorageException, RepoNotFoundException, RepoTagNotFoundException;

    @GET
    @Path("/{repoId}/{tag}")
    @ApiOperation(
      value = "Poll the submission information",
      notes = "Use this call to poll for the results of testing.",
      position = 1
    )
    Submission getSubmission(@PathParam("repoId") String repoId, @PathParam("tag") String tag)
        throws SubmissionNotFoundException, RepoStorageException, SubmissionStorageException,
            RepoNotFoundException;
}
