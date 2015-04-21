package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.Result;
import uk.ac.cam.cl.dtg.teaching.pottery.Status;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ResultDoesNotExistException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionAlreadyScheduledException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionNotFoundException;

import com.google.inject.Inject;

@Produces("application/json")
@Path("/submissions")
public class SubmissionsController {

	private static final Logger log = LoggerFactory.getLogger(SubmissionsController.class);	
	
	@Inject
	Store store;
	
	public SubmissionsController() {
		// TODO Auto-generated constructor stub
	}

	@POST
	@Path("/{submissionId}")
	public void scheduleTest(@PathParam("submissionId") String submissionId) throws SubmissionNotFoundException, SubmissionAlreadyScheduledException {
		Submission s = store.submission.get(submissionId);
		if (s==null) throw new SubmissionNotFoundException();
		if (!s.getStatus().isReceived()) throw new SubmissionAlreadyScheduledException();
		s.getStatus().setStatus(Status.STATUS_PENDING);
		store.testingQueue.add(s);
	}
	
	@GET
	@Path("/{submissionId}/status")
	public Status getStatus(@PathParam("submissionId") String submissionId) throws SubmissionNotFoundException {
		Submission s = store.submission.get(submissionId);
		if (s==null) throw new SubmissionNotFoundException();
		return s.getStatus();
	}
	
	@GET
	@Path("/{submissionId}/result")
	public Result getResult(@PathParam("submissionId") String submissionId) throws ResultDoesNotExistException, SubmissionNotFoundException {
		Submission s = store.submission.get(submissionId);
		if (s==null) throw new SubmissionNotFoundException();
		if (s.getResult() == null) throw new ResultDoesNotExistException();
		return s.getResult();
	}
	
	@GET
	@Path("/{submissionId}")
	public Submission getSubmission(@PathParam("submissionId") String submissionId) throws SubmissionNotFoundException {
		Submission s = store.submission.get(submissionId);
		if (s==null) throw new SubmissionNotFoundException();
		return s;
	}
	
}
