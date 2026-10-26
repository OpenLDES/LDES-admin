package org.openldes.ldesadmin.model.sandbox;

import java.util.List;
import java.util.Objects;

/**
 * What one component of a pipeline did in a sandbox run.
 *
 * @param index       position among the components of the same kind
 * @param component   e.g. {@code Ldio:VersionObjectCreator}
 * @param summary     what it did, in a sentence
 * @param outputs     what it made: one entry per model (adapters and transformers can make
 *                    several), or the serialisation an output sends; empty when it made nothing
 * @param contentType of the outputs, e.g. {@code text/turtle}, so they can be converted
 * @param durationMs  how long it took, for all models together
 */
public record StepResult(
    Kind kind,
    int index,
    String component,
    Status status,
    String summary,
    List<String> outputs,
    String contentType,
    Format format,
    long durationMs) {

  public enum Kind {
    INPUT, ADAPTER, TRANSFORMER, OUTPUT
  }

  public enum Status {
    OK,
    /** No model was left, e.g. the change detection filter saw it before: the run stops */
    FILTERED,
    /** The component failed, or could not be configured: the run stops, except at an output */
    ERROR
  }

  /** How the outputs are written, so they can be shown with the right highlighting */
  public enum Format {
    TURTLE, JSON, TEXT
  }

  public StepResult {
    Objects.requireNonNull(kind, "'kind' must not be null");
    Objects.requireNonNull(component, "'component' must not be null");
    Objects.requireNonNull(status, "'status' must not be null");
    Objects.requireNonNull(summary, "'summary' must not be null");
    Objects.requireNonNull(contentType, "'contentType' must not be null");
    Objects.requireNonNull(format, "'format' must not be null");
    outputs = outputs == null ? List.of() : List.copyOf(outputs);
  }
}
