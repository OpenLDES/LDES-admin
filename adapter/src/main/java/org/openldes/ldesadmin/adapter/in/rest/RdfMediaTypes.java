package org.openldes.ldesadmin.adapter.in.rest;

import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.rdf.RdfFormat;

/**
 * RDF request and response bodies. Endpoints that take RDF accept every serialisation the LDES
 * server supports.
 */
public final class RdfMediaTypes {

  public static final String TURTLE = "text/turtle";
  public static final String JSON_LD = "application/ld+json";
  public static final String N_QUADS = "application/n-quads";
  public static final String N_TRIPLES = "application/n-triples";

  private RdfMediaTypes() {
  }

  public static RdfDocument document(String body, String contentType) {
    return new RdfDocument(body, RdfFormat.fromMediaType(contentType));
  }
}
