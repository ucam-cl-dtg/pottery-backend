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

import com.google.auto.value.AutoValue;
import javax.annotation.Nonnull;

@AutoValue
abstract class ContainerSettings implements Comparable<ContainerSettings> {
  abstract String imageName();

  abstract String configurationHash();

  static ContainerSettings create(ExecutionConfig executionConfig) {
    return new AutoValue_ContainerSettings(
        executionConfig.imageName(), executionConfig.configurationHash());
  }

  @Override
  public int compareTo(@Nonnull ContainerSettings o) {
    int compare;
    compare = this.imageName().compareTo(o.imageName());
    if (compare != 0) return compare;
    return this.configurationHash().compareTo(o.configurationHash());
  }
}
