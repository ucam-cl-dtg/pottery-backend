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

public class ContextKeys {
  public static final String GENERAL_POOL_INITIAL_THREADS = "generalPoolInitialThreads";
  public static final String PARAMETERISATION_POOL_INITIAL_THREADS =
      "parameterisationPoolInitialThreads";
  public static final String REUSE_CONTAINERS = "reuseContainers";
  public static final String LOCAL_STORAGE_PREFIX = "localStoragePrefix";
  public static final String DOCKER_API_SERVER = "dockerApiServer";
  public static final String DOCKER_API_PORT = "dockerApiPort";
  public static final String DOCKER_API_MAX_CONNECTIONS = "dockerApiMaxConnections";

  public static final String DOCKER_MD5SUM_CONTAINER_OUTPUT = "dockerMd5sumContainerOutput";
  public static final String CONTAINER_TIMEOUT_MULTIPLIER = "containerTimeoutMultiplier";
}
