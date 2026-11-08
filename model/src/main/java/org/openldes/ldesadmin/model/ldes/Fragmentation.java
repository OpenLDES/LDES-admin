package org.openldes.ldesadmin.model.ldes;

import java.util.Map;

/**
 * A fragmentation strategy of a view, e.g. {@code HierarchicalTimeBasedFragmentation} with
 * properties {@code maxGranularity} and {@code fragmentationPath}.
 *
 * @param type local name of the {@code tree:} fragmentation type
 * @param properties configuration of the fragmentation, keyed by local property name
 */
public record Fragmentation(String type, Map<String, String> properties) {

  public Fragmentation {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("'type' must not be blank");
    }
    properties = properties == null ? Map.of() : Map.copyOf(properties);
  }
}
