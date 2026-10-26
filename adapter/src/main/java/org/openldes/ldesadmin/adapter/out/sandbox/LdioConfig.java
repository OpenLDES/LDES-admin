package org.openldes.ldesadmin.adapter.out.sandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;

/**
 * The configuration of a component, read as LDIO reads it: nested maps become dotted keys and
 * lists numbered ones ({@code rdf-writer.content-type}, {@code url.0}), and keys match ignoring
 * case, {@code -} and {@code _}.
 *
 * <p>Unlike LDIO, a value is never read from a file when it is a path (LDIO does that for queries
 * and mappings): the sandbox would read files of the LDES-admin host.
 */
final class LdioConfig {

  private final String component;
  /** Values by normalised key, as LDIO's ComponentProperties keeps them */
  private final Map<String, String> values;

  private LdioConfig(String component, Map<String, String> values) {
    this.component = component;
    this.values = values;
  }

  static LdioConfig of(String component, Map<String, ?> config) {
    Map<String, String> flat = new LinkedHashMap<>();
    flatten("", config, flat);
    Map<String, String> normalised = new HashMap<>();
    flat.forEach((key, value) -> normalised.put(normalise(key), value));
    return new LdioConfig(component, normalised);
  }

  String component() {
    return component;
  }

  String property(String key) {
    return optional(key).orElseThrow(() ->
        new ComponentException("%s needs '%s'".formatted(component, key)));
  }

  Optional<String> optional(String key) {
    return Optional.ofNullable(values.get(normalise(key)));
  }

  Optional<Boolean> optionalBoolean(String key) {
    return optional(key).map(Boolean::parseBoolean);
  }

  Optional<Integer> optionalInteger(String key) {
    return optional(key).map(value -> {
      try {
        return Integer.valueOf(value.strip());
      } catch (NumberFormatException e) {
        throw new ComponentException(
            "'%s' of %s must be a whole number, not %s".formatted(key, component, value));
      }
    });
  }

  /**
   * A single value or a list, as a list.
   */
  List<String> list(String key) {
    List<String> result = new ArrayList<>();
    optional(key).ifPresent(result::add);
    for (int index = 0; ; index++) {
      Optional<String> item = optional("%s.%d".formatted(key, index));
      if (item.isEmpty()) {
        return result;
      }
      result.add(item.get());
    }
  }

  /**
   * The properties under a key, e.g. those of {@code rdf-writer}, with normalised keys
   * ({@code contenttype}), as LDI expects them.
   */
  Map<String, String> nested(String key) {
    String prefix = normalise(key) + ".";
    Map<String, String> result = new HashMap<>();
    values.forEach((name, value) -> {
      if (name.startsWith(prefix)) {
        result.put(name.substring(prefix.length()), value);
      }
    });
    return result;
  }

  LdioConfig nestedConfig(String key, String nestedComponent) {
    return new LdioConfig(nestedComponent, nested(key));
  }

  private static void flatten(String prefix, Object value, Map<String, String> result) {
    if (value instanceof Map<?, ?> map) {
      map.forEach((key, nested) ->
          flatten(prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key, nested, result));
    } else if (value instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        flatten(prefix + "." + index, list.get(index), result);
      }
    } else if (value != null) {
      result.put(prefix, String.valueOf(value));
    }
  }

  private static String normalise(String key) {
    return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
  }
}
