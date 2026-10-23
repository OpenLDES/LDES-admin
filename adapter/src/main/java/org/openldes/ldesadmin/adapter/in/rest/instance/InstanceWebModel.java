package org.openldes.ldesadmin.adapter.in.rest.instance;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceOrigin;
import org.openldes.ldesadmin.model.instance.InstanceType;

public record InstanceWebModel(
    UUID id,
    InstanceType type,
    String name,
    URI url,
    InstanceOrigin origin,
    boolean sandbox,
    Instant registeredAt) {

  public static InstanceWebModel fromDomain(Instance instance) {
    return new InstanceWebModel(instance.id().value(), instance.type(), instance.name(),
        instance.url(), instance.origin(), instance.sandbox(), instance.registeredAt());
  }
}
