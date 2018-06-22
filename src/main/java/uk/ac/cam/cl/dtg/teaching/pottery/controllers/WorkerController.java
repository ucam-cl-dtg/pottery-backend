/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
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
import java.util.List;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.model.JobStatus;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class WorkerController implements uk.ac.cam.cl.dtg.teaching.pottery.api.WorkerController {

  protected static final Logger LOG = LoggerFactory.getLogger(WorkerController.class);

  private Worker worker;

  private ContainerManager containerManager;

  /** Create a new WorkerController. */
  @Inject
  public WorkerController(Worker worker, ContainerManager containerManager) {
    super();
    this.worker = worker;
    this.containerManager = containerManager;
  }

  @Override
  public List<JobStatus> listQueue() {
    return worker.getQueue();
  }

  @Override
  public Response resize(int numThreads) {
    worker.rebuildThreadPool(numThreads);
    return Response.ok().entity("{ \"message\":\"Thread pool resized\" }").build();
  }

  @Override
  public Response setTimeoutMultiplier(int multiplier) {
    containerManager.setTimeoutMultiplier(multiplier);
    return Response.ok().entity("{ \"message\":\"Thread pool resized\" }").build();
  }
}
