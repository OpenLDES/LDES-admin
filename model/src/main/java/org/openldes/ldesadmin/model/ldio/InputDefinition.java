package org.openldes.ldesadmin.model.ldio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The input of a pipeline, e.g. {@code Ldio:HttpIn}. Inputs that receive non-linked data need an
 * adapter (e.g. {@code Ldio:RdfAdapter}) that turns it into linked data.
 *
 * @param adapter the adapter of this input, {@code null} if the input produces linked data itself
 */
public record InputDefinition(
    String name, Map<String, Object> config, ComponentDefinition adapter) {

  public InputDefinition {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("'name' must not be blank");
    }
    config = config == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(config));
  }

  public Optional<ComponentDefinition> optionalAdapter() {
    return Optional.ofNullable(adapter);
  }
}
