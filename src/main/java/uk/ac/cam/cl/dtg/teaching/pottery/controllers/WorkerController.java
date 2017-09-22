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
import java.util.List;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.JobStatus;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@Produces("application/json")
@Path("/worker")
@Api(value = "/worker", description = "Manages the work queue.", position = 0)
public class WorkerController implements uk.ac.cam.cl.dtg.teaching.pottery.api.WorkerController {

  protected static final Logger LOG = LoggerFactory.getLogger(WorkerController.class);

  private Worker worker;

  private ContainerManager containerManager;

  @Inject
  public WorkerController(Worker worker, ContainerManager containerManager) {
    super();
    this.worker = worker;
    this.containerManager = containerManager;
  }

  @Override
  @GET
  @Path("/")
  @ApiOperation(
    value = "Lists queue contents",
    response = JobStatus.class,
    responseContainer = "List",
    position = 0
  )
  public List<JobStatus> listQueue() {
    return worker.getQueue();
  }

  @Override
  @POST
  @Path("/resize")
  @ApiOperation(value = "Change number of worker threads", response = Response.class)
  public Response resize(@FormParam("numThreads") int numThreads) {
    worker.rebuildThreadPool(numThreads);
    return Response.ok().entity("{ \"message\":\"Thread pool resized\" }").build();
  }

  @Override
  @POST
  @Path("/timeoutMultiplier")
  @ApiOperation(
    value =
        "Set a multiplier on the default timeout of each task stage. If the server is running "
            + "with a lot of workers and so is highly loaded then you might need to allow more "
            + "time for all tasks to run",
    response = Response.class
  )
  public Response setTimeoutMultiplier(@FormParam("multiplier") int multiplier) {
    containerManager.setTimeoutMultiplier(multiplier);
    return Response.ok().entity("{ \"message\":\"Thread pool resized\" }").build();
  }
}
