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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerConfig;
import uk.ac.cam.cl.dtg.teaching.docker.model.ContainerHostConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.task.ContainerRestrictions;

@AutoValue
abstract class ExecutionConfig {

  abstract int localUserId();

  abstract ImmutableList<PathSpecification> pathSpecification();

  abstract ImmutableList<String> command();

  abstract String imageName();

  abstract ContainerRestrictions containerRestrictions();

  static Builder builder() {
    return new AutoValue_ExecutionConfig.Builder();
  }

  ContainerConfig toContainerConfig() {
    ContainerConfig config = new ContainerConfig();
    config.setOpenStdin(true);
    config.setEnv(
        ImmutableList.of(
            "LOCAL_USER_ID=" + localUserId(),
            "RAM_LIMIT_MEGABYTES=" + containerRestrictions().getRamLimitMegabytes()));
    config.setCmd(command());
    config.setImage(imageName());
    config.setNetworkDisabled(containerRestrictions().isNetworkDisabled());
    ContainerHostConfig hc = new ContainerHostConfig();
    hc.setMemory(containerRestrictions().getRamLimitMegabytes() * 1024 * 1024);
    hc.setMemorySwap(hc.getMemory()); // disable swap
    hc.setBinds(
        pathSpecification().stream()
            .map(PathSpecification::toBindString)
            .collect(toImmutableList()));
    config.setHostConfig(hc);
    config.setVolumes(
        pathSpecification().stream()
            .collect(
                toImmutableMap(
                    pathSpecification -> pathSpecification.container().getPath(),
                    pathSpecification -> ImmutableMap.of())));
    return config;
  }

  @AutoValue.Builder
  abstract static class Builder {

    abstract ImmutableList.Builder<PathSpecification> pathSpecificationBuilder();

    Builder addPathSpecification(PathSpecification pathSpecification) {
      pathSpecificationBuilder().add(pathSpecification);
      return this;
    }

    abstract ImmutableList<PathSpecification> pathSpecification();

    abstract ImmutableList.Builder<String> commandBuilder();

    Builder addCommand(String command) {
      commandBuilder().add(command);
      return this;
    }

    abstract ImmutableList<String> command();

    abstract Builder setImageName(String imageName);

    abstract Builder setContainerRestrictions(ContainerRestrictions containerRestrictions);

    abstract Builder setLocalUserId(int localUserId);

    abstract ExecutionConfig build();
  }
}
