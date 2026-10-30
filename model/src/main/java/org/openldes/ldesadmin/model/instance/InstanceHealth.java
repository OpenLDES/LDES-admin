package org.openldes.ldesadmin.model.instance;

import java.time.Instant;
import java.util.Objects;

/** The health of an instance at a certain moment in time. */
public record InstanceHealth(HealthStatus status, Instant checkedAt) {

  public InstanceHealth {
    Objects.requireNonNull(status, "'status' must not be null");
    Objects.requireNonNull(checkedAt, "'checkedAt' must not be null");
  }

  public static InstanceHealth up(Instant checkedAt) {
    return new InstanceHealth(HealthStatus.UP, checkedAt);
  }

  public static InstanceHealth down(Instant checkedAt) {
    return new InstanceHealth(HealthStatus.DOWN, checkedAt);
  }

  public static InstanceHealth unknown(Instant checkedAt) {
    return new InstanceHealth(HealthStatus.UNKNOWN, checkedAt);
  }

  public boolean isUp() {
    return status == HealthStatus.UP;
  }
}
