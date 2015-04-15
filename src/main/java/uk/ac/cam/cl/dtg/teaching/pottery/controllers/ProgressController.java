package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.teaching.pottery.Code;
import uk.ac.cam.cl.dtg.teaching.pottery.Progress;
import uk.ac.cam.cl.dtg.teaching.pottery.Store;
import uk.ac.cam.cl.dtg.teaching.pottery.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ProgressNotFoundException;


@Produces("application/json")
@Path("/progress")
public class ProgressController {

	private static final Logger log = LoggerFactory.getLogger(ProgressController.class);
	
	public ProgressController() {
		// TODO Auto-generated constructor stub
	}

	@GET
	@Path("/{progressId}")
	public Progress getProgress(@PathParam("progressId") String progressId) throws ProgressNotFoundException {
		Progress p = Store.progress.get(progressId);
		if (p==null) throw new ProgressNotFoundException();
		return p;
	}
	
	@POST
	@Path("/{progressId}")
	@Consumes("multipart/form-data")
	public Submission makeSubmission(@PathParam("progressId") String progressId, @MultipartForm Code code) throws ProgressNotFoundException {
		Progress p = Store.progress.get(progressId);
		if (p== null) throw new ProgressNotFoundException();
		Submission s = new Submission();
		s.setSubmissionId(UUID.randomUUID().toString());
		s.setProgressId(progressId);
		s.setCode(code);
		p.addSubmission(s);
		Store.submission.put(s.getSubmissionId(),s);
		return s;
	}
}
