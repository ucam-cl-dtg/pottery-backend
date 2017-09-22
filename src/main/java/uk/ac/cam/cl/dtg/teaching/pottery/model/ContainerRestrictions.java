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

package uk.ac.cam.cl.dtg.teaching.pottery.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ContainerRestrictions {

  private int timeoutSec;

  private int diskWriteLimitMegabytes;

  private int ramLimitMegabytes;

  private boolean networkDisabled;

  @JsonCreator
  public ContainerRestrictions(
      @JsonProperty("timeoutSec") int timeoutSec,
      @JsonProperty("diskWriteLimitMegabytes") int diskWriteLimitMegabytes,
      @JsonProperty("ramLimitMegabytes") int ramLimitMegabytes,
      @JsonProperty("networkDisabled") boolean networkDisabled) {
    super();
    this.timeoutSec = timeoutSec;
    this.diskWriteLimitMegabytes = diskWriteLimitMegabytes;
    this.ramLimitMegabytes = ramLimitMegabytes;
    this.networkDisabled = networkDisabled;
  }

  /** Restrictions to impose on a candidate. */
  public static ContainerRestrictions candidateRestriction(ContainerRestrictions v) {
    if (v != null) {
      return v;
    }
    return new ContainerRestrictions(60, 1, 200, true);
  }

  /** Restrictions for an author of a test: to compile the task itself. */
  public static ContainerRestrictions authorRestriction(ContainerRestrictions v) {
    if (v != null) {
      return v;
    }
    return new ContainerRestrictions(500, 50, 500, false);
  }

  public boolean isNetworkDisabled() {
    return networkDisabled;
  }

  public int getTimeoutSec() {
    return timeoutSec;
  }

  public int getDiskWriteLimitMegabytes() {
    return diskWriteLimitMegabytes;
  }

  public int getRamLimitMegabytes() {
    return ramLimitMegabytes;
  }
}
