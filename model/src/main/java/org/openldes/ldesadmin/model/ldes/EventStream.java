package org.openldes.ldesadmin.model.ldes;

import java.util.List;
import java.util.Objects;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * An event stream (collection) configured on an LDES server.
 *
 * @param timestampPath property path holding the timestamp of a member, may be {@code null}
 * @param versionOfPath property path linking a version to its state object, may be {@code null}
 * @param createVersions whether the server turns ingested state objects into version objects
 * @param views names of the views of this event stream
 * @param definition the event stream as returned by the admin API
 */
public record EventStream(
    String collection,
    String timestampPath,
    String versionOfPath,
    boolean createVersions,
    List<String> views,
    RdfDocument definition) {

  public EventStream {
    if (collection == null || collection.isBlank()) {
      throw new IllegalArgumentException("'collection' must not be blank");
    }
    Objects.requireNonNull(definition, "'definition' must not be null");
    views = views == null ? List.of() : List.copyOf(views);
  }
}
