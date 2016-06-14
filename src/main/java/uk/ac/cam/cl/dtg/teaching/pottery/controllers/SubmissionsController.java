package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
import java.sql.SQLException;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionAlreadyScheduledException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.RepoFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@Produces("application/json")
@Path("/submissions")
@Api(value = "/submissions", description = "Manages requests for testing",position=2)
public class SubmissionsController {

	protected static final Logger log = LoggerFactory.getLogger(SubmissionsController.class);	
	
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

	@POST
	@Path("/{repoId}/{tag}")
	@ApiOperation(value="Schedules a test by creating a submission",
			notes="A submission is created from a tag in the code repository used by the candidate.",position=0)
	public Submission scheduleTest(@PathParam("repoId") String repoId, @PathParam("tag") String tag) throws SubmissionNotFoundException, SubmissionAlreadyScheduledException, RepoException, IOException, SQLException {
		Repo r = repoFactory.getInstance(repoId);
		return r.scheduleSubmission(tag, worker,database);
	}
	
	@GET
	@Path("/{repoId}/{tag}")
	@ApiOperation(value="Poll the submission information",
		notes="Use this call to poll for the results of testing.",position=1)
	public Submission getSubmission(@PathParam("repoId") String repoId, @PathParam("tag") String tag) throws SubmissionNotFoundException, SQLException, RepoException {
		return repoFactory.getInstance(repoId).getSubmission(tag,database);
	}
}
