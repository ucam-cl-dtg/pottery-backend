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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Named;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.model.JobStatus;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class WorkerController implements uk.ac.cam.cl.dtg.teaching.pottery.api.WorkerController {

  protected static final Logger LOG = LoggerFactory.getLogger(WorkerController.class);

  private Worker worker;
  private Worker parameterisationWorker;

  private ContainerManager containerManager;

  /** Create a new WorkerController. */
  @Inject
  public WorkerController(Worker worker,
                          @Named(Repo.PARAMETERISATION_WORKER_NAME) Worker parameterisationWorker,
                          ContainerManager containerManager) {
    super();
    this.worker = worker;
    this.parameterisationWorker = parameterisationWorker;
    this.containerManager = containerManager;
  }

  @Override
  public List<JobStatus> listQueue() {
    return Stream.concat(worker.getQueue().stream(), parameterisationWorker.getQueue().stream())
        .collect(Collectors.toList());
  }

  @Override
  public Response resize(int numThreads) {
    worker.rebuildThreadPool(numThreads);
    parameterisationWorker.rebuildThreadPool(numThreads);
    return Response.ok().entity("{ \"message\":\"Thread pool resized\" }").build();
  }

  @Override
  public Response setTimeoutMultiplier(int multiplier) {
    containerManager.setTimeoutMultiplier(multiplier);
    return Response.ok().entity("{ \"message\":\"Thread pool resized\" }").build();
  }
}
