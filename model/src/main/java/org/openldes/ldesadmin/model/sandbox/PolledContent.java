package org.openldes.ldesadmin.model.sandbox;

import java.util.Objects;

/**
 * What a {@link Poll} received. LDIO hands both the body and its content type to the adapter of
 * the pipeline, so a response without a content type never reaches a pipeline.
 *
 * @param contentType the Content-Type header as received, e.g. {@code application/json;
 *                    charset=utf-8}
 */
public record PolledContent(String contentType, String body) {

  public PolledContent {
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("'contentType' must not be blank");
    }
    Objects.requireNonNull(body, "'body' must not be null");
  }
}
