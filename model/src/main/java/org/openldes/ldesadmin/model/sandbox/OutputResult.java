package org.openldes.ldesadmin.model.sandbox;

import java.util.Objects;

/**
 * What an output component made of one model.
 *
 * @param serialisation what it sends or writes, e.g. the model in its RDF format, or a SPARQL
 *                      update; empty when it writes nothing
 * @param contentType   of the serialisation, e.g. {@code text/turtle}
 * @param summary       what it did, or would do in a dry run, e.g. "Would POST to …"
 */
public record OutputResult(String serialisation, String contentType, String summary) {

  public OutputResult {
    Objects.requireNonNull(serialisation, "'serialisation' must not be null");
    Objects.requireNonNull(contentType, "'contentType' must not be null");
    Objects.requireNonNull(summary, "'summary' must not be null");
  }
}
