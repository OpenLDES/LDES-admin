package org.openldes.ldesadmin.model.ldio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A component of a pipeline (adapter, transformer or output) and its configuration, e.g. {@code
 * Ldio:HttpOut} with {@code endpoint: http://localhost:8080/my-ldes}.
 *
 * @param config component configuration; values are strings or nested maps (e.g. {@code
 *     rdf-writer.content-type}), in the order they were defined
 */
public record ComponentDefinition(String name, Map<String, Object> config) {

  public ComponentDefinition {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("'name' must not be blank");
    }
    config = config == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(config));
  }

  public static ComponentDefinition of(String name) {
    return new ComponentDefinition(name, Map.of());
  }
}
