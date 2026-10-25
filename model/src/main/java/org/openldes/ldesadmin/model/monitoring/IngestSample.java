package org.openldes.ldesadmin.model.monitoring;

import java.time.Instant;
import java.util.Objects;
import org.openldes.ldesadmin.model.instance.InstanceId;

/**
 * The total number of members an LDES server had ingested at a moment in time, summed over its
 * event streams.
 */
public record IngestSample(InstanceId instanceId, Instant time, long ingestedMembers) {

  public IngestSample {
    Objects.requireNonNull(instanceId, "'instanceId' must not be null");
    Objects.requireNonNull(time, "'time' must not be null");
  }
}
