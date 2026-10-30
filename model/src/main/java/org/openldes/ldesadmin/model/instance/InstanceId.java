package org.openldes.ldesadmin.model.instance;

import java.util.Objects;
import java.util.UUID;

/** Identifies an LDES server or LDIO managed by LDES-admin. */
public record InstanceId(UUID value) {

  public InstanceId {
    Objects.requireNonNull(value, "'value' must not be null");
  }

  public static InstanceId randomId() {
    return new InstanceId(UUID.randomUUID());
  }

  public static InstanceId of(String value) {
    return new InstanceId(UUID.fromString(value));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
