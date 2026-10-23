package org.openldes.ldesadmin.model.ldes;

import java.util.Map;

/**
 * A retention policy of a view or event source, e.g. {@code DurationAgoPolicy} with property
 * {@code value = PT2M}, or {@code LatestVersionSubset} with property {@code amount = 2}.
 *
 * @param type       local name of the {@code ldes:} retention policy type
 * @param properties configuration of the policy, keyed by local property name
 */
public record RetentionPolicy(String type, Map<String, String> properties) {

  public RetentionPolicy {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("'type' must not be blank");
    }
    properties = properties == null ? Map.of() : Map.copyOf(properties);
  }
}
