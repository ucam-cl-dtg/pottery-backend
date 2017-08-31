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

import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

@Produces("application/json")
@Path("/status")
@Api(value = "/status", description = "Server status",position=0)
public class StatusController {

	protected static final Logger LOG = LoggerFactory.getLogger(WorkerController.class);

	private Worker worker;

	private ContainerManager containerManager;
	
	@Inject
	public StatusController(Worker worker, ContainerManager containerManager, TaskIndex taskIndex) {
		super();
		this.worker = worker;
		this.containerManager = containerManager;
	}
		
	@GET
	@Path("/")
	@ApiOperation(value="Get server status",response=String.class,responseContainer="Map",position=0)
	public Map<String,String> getStatus() {
		Map<String,String> response = new TreeMap<>();
		response.put("Worker.numThreads",worker.getNumThreads()+"");
		response.put("Worker.queueSize", worker.getQueue().size()+"");
		response.put("Worker.smoothedWaitTime", worker.getSmoothedWaitTime()+"");
		response.put("ContainerManager.smoothedCallTime",containerManager.getSmoothedCallTime()+"");
		response.put("ContainerManager.apiStatus",containerManager.getApiStatus());
		return response;
	}

	
}
