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
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.repo.Repo;
import uk.ac.cam.cl.dtg.teaching.pottery.ssh.SshManager;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

public class StatusController implements uk.ac.cam.cl.dtg.teaching.pottery.api.StatusController {

  protected static final Logger LOG = LoggerFactory.getLogger(WorkerController.class);

  private final Worker worker;
  private final ContainerEnvConfig containerEnvConfig;
  private final ContainerManager containerManager;
  private final SshManager sshManager;

  /** Create a new StatusController. */
  @Inject
  public StatusController(
      Worker worker,
      ContainerEnvConfig containerEnvConfig,
      ContainerManager containerManager,
      SshManager sshManager) {
    super();
    this.worker = worker;
    this.containerEnvConfig = containerEnvConfig;
    this.containerManager = containerManager;
    this.sshManager = sshManager;
  }

  @Override
  public Map<String, String> getStatus() {
    Map<String, String> response = new TreeMap<>();
    response.put("Worker.numThreads", String.valueOf(worker.getNumThreads()));
    response.put("Worker.queueSize", String.valueOf(worker.getQueue().size()));
    response.put("Worker.smoothedWaitTime", String.valueOf(worker.getSmoothedWaitTime()));
    response.put(
        "ContainerManager.smoothedCallTime",
        String.valueOf(containerManager.getSmoothedCallTime()));
    response.put("ContainerManager.apiStatus", String.valueOf(containerManager.getApiStatus()));
    response.put(
        "Pottery.user",
        String.format("%s(%d)", containerEnvConfig.getUserName(), containerEnvConfig.getUid()));
    return response;
  }

  @Override
  public String checkDockerVersion() {
    try {
      return containerManager.getVersion();
    } catch (ApiUnavailableException e) {
      return "UNAVAILABLE: " + e.getMessage();
    }
  }

  @Override
  public String publicKey() {
    return sshManager.getPublicKey();
  }
}
