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

package uk.ac.cam.cl.dtg.teaching.pottery.config;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import javax.inject.Named;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerEnvConfig {

  protected static final Logger LOG = LoggerFactory.getLogger(ContainerEnvConfig.class);

  private String userName;

  private int uid;

  private File libDir;

  @Inject
  public ContainerEnvConfig(@Named("localStoragePrefix") String prefix) {
    this.userName = System.getProperty("user.name");
    this.uid = getUidForUserName(userName);
    this.libDir = new File(prefix, "lib");
  }

  private static int getUidForUserName(String userName) {
    try {
      Process child = Runtime.getRuntime().exec("/usr/bin/id -u " + userName);
      try {
        return Integer.parseInt(IOUtils.readLines(child.getInputStream()).get(0));
      } finally {
        child.destroyForcibly();
      }
    } catch (NumberFormatException e) {
      LOG.error("Failed to parse UID", e);
      return 0;
    } catch (IOException e) {
      LOG.error("Failed to run id to find UID");
      return 0;
    }
  }

  public String getUserName() {
    return userName;
  }

  public int getUid() {
    return uid;
  }

  public File getLibRoot() {
    return libDir;
  }

  public String getContainerPrefix() {
    return "pottery-transient-";
  }
}
