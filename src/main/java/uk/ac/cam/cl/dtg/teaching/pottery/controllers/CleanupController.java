package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import uk.ac.cam.cl.dtg.teaching.pottery.Cleanup;
import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;

@Produces("application/json")
@Path("/cleanup")
@Api(value = "/cleanup", description = "Bookkeeping stuff",position=1)
public class CleanupController {

	private Database database;
	private Config config;

	@Inject
	public CleanupController(Database database, Config config) {
		super();
		this.database = database;
		this.config = config;
	}

	@POST
	@Path("/")
	@ApiOperation(value="Clean up")
	public List<String> cleanup() throws SQLException, IOException {
		return Cleanup.cleanup(database, config);
	}
}
