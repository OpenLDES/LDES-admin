package org.openldes.ldesadmin.model.ldio;

import java.util.Collection;
import java.util.List;

/**
 * The components installed on an LDIO, by kind.
 */
public record ComponentCatalog(List<String> inputs, List<String> adapters,
                               List<String> transformers, List<String> outputs) {

  public ComponentCatalog {
    inputs = sorted(inputs);
    adapters = sorted(adapters);
    transformers = sorted(transformers);
    outputs = sorted(outputs);
  }

  private static List<String> sorted(Collection<String> names) {
    return names == null ? List.of() : names.stream().sorted().toList();
  }
}
