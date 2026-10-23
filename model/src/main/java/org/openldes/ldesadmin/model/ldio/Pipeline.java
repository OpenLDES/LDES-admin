package org.openldes.ldesadmin.model.ldio;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The definition of an LDIO pipeline: one input (with an optional adapter), a chain of
 * transformers and one or more outputs.
 *
 * <pre>
 * input (+ adapter) ──▶ transformer ──▶ … ──▶ transformer ──┬──▶ output
 *                                                             └──▶ output
 * </pre>
 */
public record Pipeline(
    String name,
    String description,
    InputDefinition input,
    List<ComponentDefinition> transformers,
    List<ComponentDefinition> outputs) {

  /**
   * The name ends up in URLs (e.g. {@code Ldio:HttpIn} listens on {@code /{pipeline name}}).
   */
  private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

  public Pipeline {
    if (name == null || !VALID_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "'name' must consist of letters, digits, '-' and '_', but was " + name);
    }
    Objects.requireNonNull(input, "'input' must not be null");
    description = description == null ? "" : description;
    transformers = transformers == null ? List.of() : List.copyOf(transformers);
    outputs = outputs == null ? List.of() : List.copyOf(outputs);
  }
}
