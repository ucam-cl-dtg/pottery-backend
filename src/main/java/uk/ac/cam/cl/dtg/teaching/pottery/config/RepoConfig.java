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

import java.io.File;

public class RepoConfig {

  private File prefix;

  public RepoConfig() {
    this.prefix = new File(Config.PREFIX, "repos");
  }

  public RepoConfig(File repoPrefix) {
    this.prefix = repoPrefix;
  }

  public File getRepoRoot() {
    return new File(prefix, "repos");
  }

  public File getRepoTestingRoot() {
    return new File(prefix, "repo-testing");
  }

  public String getWebtagPrefix() {
    return "online-";
  }

  public File getRepoDir(String repoId) {
    return new File(getRepoRoot(), repoId);
  }

  public File getRepoTestingDir(String repoId) {
    return new File(getRepoTestingRoot(), repoId);
  }
}
