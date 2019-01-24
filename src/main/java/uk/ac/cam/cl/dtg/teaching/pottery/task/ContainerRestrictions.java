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
package uk.ac.cam.cl.dtg.teaching.pottery.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ContainerRestrictions {

  private Integer timeoutSec;

  private Integer diskWriteLimitMegabytes;

  private Integer ramLimitMegabytes;

  private Integer outputLimitKilochars;

  private Boolean networkDisabled;

  private final boolean fullySpecified;

  @JsonCreator
  public ContainerRestrictions(
      @JsonProperty("timeoutSec") Integer timeoutSec,
      @JsonProperty("diskWriteLimitMegabytes") Integer diskWriteLimitMegabytes,
      @JsonProperty("ramLimitMegabytes") Integer ramLimitMegabytes,
      @JsonProperty("outputLimitKilochars") Integer outputLimitKilochars,
      @JsonProperty("networkDisabled") Boolean networkDisabled) {
    super();
    this.timeoutSec = timeoutSec;
    this.diskWriteLimitMegabytes = diskWriteLimitMegabytes;
    this.ramLimitMegabytes = ramLimitMegabytes;
    this.outputLimitKilochars = outputLimitKilochars;
    this.networkDisabled = networkDisabled;
    this.fullySpecified = this.timeoutSec != null && this.diskWriteLimitMegabytes !=  null
        && this.ramLimitMegabytes != null && this.outputLimitKilochars != null
        && this.networkDisabled != null;

  }

  public static final ContainerRestrictions DEFAULT_CANDIDATE_RESTRICTIONS =
      new ContainerRestrictions(60, 1, 200, 100, true);
  public static final ContainerRestrictions DEFAULT_AUTHOR_RESTRICTIONS =
      new ContainerRestrictions(500, 50, 500, 50000, false);

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

  public int getOutputLimitKilochars() {
    return outputLimitKilochars;
  }

  ContainerRestrictions withDefaultContainerRestriction(ContainerRestrictions defaultRestrictions) {
    if (fullySpecified) {
      return this;
    }
    return new ContainerRestrictions(
        timeoutSec != null ? timeoutSec : defaultRestrictions.timeoutSec,
        diskWriteLimitMegabytes != null ? diskWriteLimitMegabytes
            : defaultRestrictions.diskWriteLimitMegabytes,
        ramLimitMegabytes != null ? ramLimitMegabytes : defaultRestrictions.ramLimitMegabytes,
        outputLimitKilochars != null ? outputLimitKilochars
            : defaultRestrictions.outputLimitKilochars,
        networkDisabled != null ? networkDisabled : defaultRestrictions.networkDisabled
    );
  }
}
