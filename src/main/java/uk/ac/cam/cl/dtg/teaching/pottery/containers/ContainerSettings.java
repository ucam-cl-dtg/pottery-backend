package uk.ac.cam.cl.dtg.teaching.pottery.containers;

import com.google.auto.value.AutoValue;
import javax.annotation.Nonnull;

@AutoValue
abstract class ContainerSettings implements Comparable<ContainerSettings> {
  abstract String imageName();
  abstract String configurationHash();

  static ContainerSettings create(ExecutionConfig executionConfig) {
    return new AutoValue_ContainerSettings(executionConfig.imageName(), executionConfig.configurationHash());
  }

  @Override
  public int compareTo(@Nonnull ContainerSettings o) {
    int compare;
    compare = this.imageName().compareTo(o.imageName());
    if (compare != 0) return compare;
    return this.configurationHash().compareTo(o.configurationHash());
  }
}