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
package uk.ac.cam.cl.dtg.teaching.pottery.api;

import com.wordnik.swagger.annotations.ApiOperation;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.JobStatus;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.List;

public interface WorkerController {
    @GET
    @Path("/")
    @ApiOperation(
      value = "Lists queue contents",
      response = JobStatus.class,
      responseContainer = "List",
      position = 0
    )
    List<JobStatus> listQueue();

    @POST
    @Path("/resize")
    @ApiOperation(value = "Change number of worker threads", response = Response.class)
    Response resize(@FormParam("numThreads") int numThreads);

    @POST
    @Path("/timeoutMultiplier")
    @ApiOperation(
      value =
          "Set a multiplier on the default timeout of each task stage. If the server is running "
              + "with a lot of workers and so is highly loaded then you might need to allow more "
              + "time for all tasks to run",
      response = Response.class
    )
    Response setTimeoutMultiplier(@FormParam("multiplier") int multiplier);
}
