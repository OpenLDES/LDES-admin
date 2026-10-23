package org.openldes.ldesadmin.model.ldes;

import java.util.List;
import java.util.Objects;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * A view on an event stream: how the members are fragmented into pages and how long they are
 * retained.
 *
 * @param fragmentations fragmentation strategies, applied in order; empty for plain pagination
 * @param definition     the view as returned by the admin API
 */
public record View(
    String collection,
    String name,
    int pageSize,
    List<Fragmentation> fragmentations,
    List<RetentionPolicy> retentionPolicies,
    RdfDocument definition) {

  public View {
    if (collection == null || collection.isBlank()) {
      throw new IllegalArgumentException("'collection' must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("'name' must not be blank");
    }
    Objects.requireNonNull(definition, "'definition' must not be null");
    fragmentations = fragmentations == null ? List.of() : List.copyOf(fragmentations);
    retentionPolicies = retentionPolicies == null ? List.of() : List.copyOf(retentionPolicies);
  }
}
