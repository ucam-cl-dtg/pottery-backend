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

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionAlreadyScheduledException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionNotFoundException;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

@Produces("application/json")
@Path("/submissions")
@Api(value = "/submissions", description = "Manages requests for testing",position=2)
public class SubmissionsController {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(SubmissionsController.class);	
	
	private Store store;
	
	private Database database;
	
	@Inject	
	public SubmissionsController(Store store, Database database) {
		super();
		this.store = store;
		this.database = database;
	}

	@POST
	@Path("/{repoId}/{tag}")
	@ApiOperation(value="Schedules a test by creating a submission",
			notes="A submission is created from a tag in the code repository used by the candidate.",position=0)
	public Submission scheduleTest(@PathParam("repoId") String repoId, @PathParam("tag") String tag) throws SubmissionNotFoundException, SubmissionAlreadyScheduledException, RepoException, IOException, SQLException {
		return store.createSubmission(repoId, tag);
	}
	
	@GET
	@Path("/{repoId}/{tag}")
	@ApiOperation(value="Poll the submission information",
		notes="Use this call to poll for the results of testing.",position=1)
	public Submission getSubmission(@PathParam("repoId") String repoId, @PathParam("tag") String tag) throws SubmissionNotFoundException, SQLException {
		try (TransactionQueryRunner q = database.getQueryRunner()) {
			return Submission.getByRepoIdAndTag(repoId, tag, q);
		}
	}
	

}
