package org.openldes.ldesadmin.model.sandbox;

import java.util.Objects;

/**
 * What the input of a pipeline receives in a sandbox run: a message as it is posted to an HTTP
 * input, polled, consumed from a queue, or a member of an LDES. The adapter turns it into linked
 * data.
 *
 * @param contentType e.g. {@code application/json}; the adapter decides which ones it reads
 * @param source      the URL it was polled from, if it was; {@code null} otherwise
 */
public record RunInput(String content, String contentType, String source) {

  /** As much as the sandbox handles comfortably, as for polled responses */
  public static final int MAX_LENGTH = 5 * 1024 * 1024;

  public RunInput {
    Objects.requireNonNull(content, "'content' must not be null");
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("'contentType' must not be blank");
    }
    if (content.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("The input is larger than 5 MB, too much for the sandbox");
    }
    source = source == null || source.isBlank() ? null : source;
  }

  public static RunInput of(String content, String contentType) {
    return new RunInput(content, contentType, null);
  }
}
