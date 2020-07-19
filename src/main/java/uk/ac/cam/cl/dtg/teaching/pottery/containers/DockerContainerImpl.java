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
package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.docker.DockerPatch;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.ContainerRetryNeededException;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.config.ContainerEnvConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.ContainerExecutionException;

/** Docker implementation of container backend. */
public class DockerContainerImpl extends DockerContainer implements ContainerBackend {
  private static final Logger LOG = LoggerFactory.getLogger(DockerContainerImpl.class);

  private final AtomicInteger containerNameCounter = new AtomicInteger(0);

  private final ConcurrentSkipListSet<String> runningContainers = new ConcurrentSkipListSet<>();

  @Inject
  public DockerContainerImpl(ContainerEnvConfig config) throws IOException {
    super(config);
  }

  @Override
  protected Collection<String> getRunningContainers() {
    return runningContainers;
  }

  @Override
  public synchronized String getVersion() throws ApiUnavailableException {
    return "Docker:" + getDockerApi().getVersion().getApiVersion();
  }

  @Override
  public String getInternalMountPath() {
    return "/mnt/pottery";
  }

  @Override
  protected ContainerExecResponse executeContainerInner(ExecutionConfig executionConfig)
      throws ApiUnavailableException, ContainerExecutionException, ContainerRetryNeededException {

    // Make sure all the mount points exist, otherwise docker creates them for us with the wrong
    // file permissions
    for (PathSpecification specification : executionConfig.pathSpecification()) {
      try {
        FileUtil.mkdirIfNotExists(specification.host());
      } catch (IOException e) {
        throw new ContainerExecutionException(
            "Failed to create mount point " + specification.host(), "", e);
      }
    }

    String containerName =
        this.config.getContainerPrefix() + containerNameCounter.incrementAndGet();
    LOG.debug("Creating container {}", containerName);

    DockerApi docker = getDockerApi();

    try {
      ContainerConfig config = executionConfig.toContainerConfig();
      ContainerResponse response = docker.createContainer(containerName, config);
      final String containerId = response.getId();
      getRunningContainers().add(containerId);
      try {
        return startContainer(executionConfig, containerName, containerId, docker);
      } finally {
        getRunningContainers().remove(containerId);
        DockerPatch.deleteContainer(docker, containerId, true, true);
      }
    } catch (RuntimeException e) {
      LOG.error("Error executing container", e);
      throw new ContainerExecutionException(
          String.format(
              "An error (%s) occurred when executing container: %s",
              e.getClass().getName(), e.getMessage()),
          containerName);
    }
  }
}
