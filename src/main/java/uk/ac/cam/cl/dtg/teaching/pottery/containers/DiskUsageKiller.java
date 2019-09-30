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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.docker.DockerUtil;
import uk.ac.cam.cl.dtg.teaching.docker.api.DockerApi;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerInfo;

public class DiskUsageKiller implements Runnable {

  protected static final Logger LOG = LoggerFactory.getLogger(DiskUsageKiller.class);

  private final String containerId;
  private final int maxBytes;
  private final DockerContainer containerManager;

  private boolean killed = false;
  private int bytesWritten = 0;

  private AttachListener attachListener;

  public DiskUsageKiller(
      String containerId, DockerContainer containerManager, int maxBytes, AttachListener l) {
    this.containerId = containerId;
    this.containerManager = containerManager;
    this.maxBytes = maxBytes;
    this.attachListener = l;
  }

  @Override
  public void run() {
    try {
      DockerApi docker = containerManager.getDockerApi();
      ContainerInfo i = docker.inspectContainer(containerId, true);
      if (i != null && i.getSizeRw() > this.maxBytes) {
        boolean killed = DockerUtil.killContainer(containerId, docker);
        attachListener.notifyClose();
        synchronized (this) {
          this.bytesWritten = i.getSizeRw();
          this.killed = killed;
        }
      }
    } catch (RuntimeException e) {
      if (!e.getMessage().startsWith("No such container: ")) {
        // avoid the race condition where the container exits just before we kill it
        LOG.error("Caught exception when trying to kill container for disk usage", e);
      }
    } catch (ApiUnavailableException e) {
      // Just ignore this one - we'll be rerun in a few seconds (or cancelled by the managing
      // thread)
    }
  }

  public synchronized boolean isKilled() {
    return this.killed;
  }

  public synchronized int getBytesWritten() {
    return this.bytesWritten;
  }
}
