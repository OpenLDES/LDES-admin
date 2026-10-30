package org.openldes.ldesadmin.model.rdf;

import java.util.Arrays;
import java.util.Locale;

/** RDF serialisations used by the OpenLDES APIs. */
public enum RdfFormat {
  TURTLE("text/turtle"),
  JSON_LD("application/ld+json"),
  N_QUADS("application/n-quads"),
  N_TRIPLES("application/n-triples");

  private final String mediaType;

  RdfFormat(String mediaType) {
    this.mediaType = mediaType;
  }

  public String mediaType() {
    return mediaType;
  }

  /** Finds the format for a media type, ignoring parameters such as {@code charset}. */
  public static RdfFormat fromMediaType(String mediaType) {
    String type = mediaType.split(";")[0].strip().toLowerCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(format -> format.mediaType.equals(type))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported RDF format: " + mediaType));
  }
}
