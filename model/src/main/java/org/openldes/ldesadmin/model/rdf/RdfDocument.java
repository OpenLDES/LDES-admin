package org.openldes.ldesadmin.model.rdf;

import java.util.Objects;

/**
 * An RDF document in a given serialisation. The LDES server admin API describes event streams,
 * views, shapes and DCAT in RDF, so LDES-admin passes these documents around as-is.
 */
public record RdfDocument(String content, RdfFormat format) {

  public RdfDocument {
    Objects.requireNonNull(content, "'content' must not be null");
    Objects.requireNonNull(format, "'format' must not be null");
  }

  public static RdfDocument turtle(String content) {
    return new RdfDocument(content, RdfFormat.TURTLE);
  }
}
