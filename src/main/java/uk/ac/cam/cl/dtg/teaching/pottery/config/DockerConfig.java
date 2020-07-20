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
package uk.ac.cam.cl.dtg.teaching.pottery.config;

import javax.inject.Inject;
import javax.inject.Named;

public class DockerConfig {

  private final String server;
  private final int port;
  private final int maxConnections;
  private final boolean md5SumContainerOutput;

  @Inject
  public DockerConfig(
      @Named(ContextKeys.DOCKER_API_SERVER) String server,
      @Named(ContextKeys.DOCKER_API_PORT) int port,
      @Named(ContextKeys.DOCKER_API_MAX_CONNECTIONS) int maxConnections,
      @Named(ContextKeys.DOCKER_MD5SUM_CONTAINER_OUTPUT) boolean md5SumContainerOutput) {
    this.server = server;
    this.port = port;
    this.maxConnections = maxConnections;
    this.md5SumContainerOutput = md5SumContainerOutput;
  }

  public boolean validateMd5SumContainerOutput() {
    return md5SumContainerOutput;
  }

  public String getServer() {
    return server;
  }

  public int getPort() {
    return port;
  }

  public int getMaxConnections() {
    return maxConnections;
  }
}
