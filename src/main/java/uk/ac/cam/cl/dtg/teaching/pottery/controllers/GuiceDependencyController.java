package uk.ac.cam.cl.dtg.teaching.pottery.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

@Produces("application/json")
@Path("/guice")
@Api(value = "/guice", description = "Guice dependency introspection",position=1)
public class GuiceDependencyController {

	private Injector injector;
	
	@Inject
	public GuiceDependencyController(Injector injector) {
		super();
		this.injector = injector;
	}

	@GET
	@Produces("text/vnd.graphviz")
	@Path("/dependency-graph")
	@ApiOperation(value="Return the object dependency graph")
	public Response getDependencyGraph() throws IOException {
		StringWriter out = new StringWriter();
		try(PrintWriter pw = new PrintWriter(out)) {
			Injector i = Guice.createInjector(new GraphvizModule());
			GraphvizGrapher grapher = i.getInstance(GraphvizGrapher.class);
			grapher.setOut(pw);
			grapher.setRankdir("TB");
			grapher.graph(this.injector);
		}
		return Response.ok(out.toString(),MediaType.TEXT_PLAIN).build();
	}
}
