package org.openldes.ldesadmin.adapter.in.rest.instance;

import java.time.Instant;
import org.openldes.ldesadmin.model.instance.HealthStatus;
import org.openldes.ldesadmin.model.instance.InstanceHealth;

public record InstanceHealthWebModel(HealthStatus status, Instant checkedAt) {

  public static InstanceHealthWebModel fromDomain(InstanceHealth health) {
    return new InstanceHealthWebModel(health.status(), health.checkedAt());
  }
}
