package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Produces("application/json")
@Path("/tasks")
public class TasksController {

	private static final Logger log = LoggerFactory.getLogger(TasksController.class);

	@GET
	@Path("/test")
	public String test() {
		return "test";
	}
}
