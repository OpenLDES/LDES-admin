package org.openldes.ldesadmin.adapter.out.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The samples of a Prometheus text exposition, as served by {@code /actuator/prometheus} of the
 * LDES server and LDIO.
 */
public final class PrometheusMetrics {

  private static final Pattern SAMPLE =
      Pattern.compile("^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\\{(.*)})?\\s+(\\S+)(?:\\s+\\S+)?$");
  private static final Pattern LABEL =
      Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)=\"((?:[^\"\\\\]|\\\\.)*)\"");

  private final List<Sample> samples;

  private PrometheusMetrics(List<Sample> samples) {
    this.samples = samples;
  }

  public static PrometheusMetrics parse(String exposition) {
    List<Sample> samples = new ArrayList<>();
    exposition
        .lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .forEach(
            line -> {
              Matcher matcher = SAMPLE.matcher(line);
              if (matcher.matches()) {
                samples.add(
                    new Sample(
                        matcher.group(1), labels(matcher.group(2)), value(matcher.group(3))));
              }
            });
    return new PrometheusMetrics(List.copyOf(samples));
  }

  public Stream<Sample> samples(String name) {
    return samples.stream().filter(sample -> sample.name().equals(name));
  }

  private static Map<String, String> labels(String labels) {
    Map<String, String> result = new LinkedHashMap<>();
    if (labels != null) {
      Matcher matcher = LABEL.matcher(labels);
      while (matcher.find()) {
        result.put(matcher.group(1), matcher.group(2).replaceAll("\\\\(.)", "$1"));
      }
    }
    return result;
  }

  private static double value(String value) {
    return switch (value) {
      case "+Inf" -> Double.POSITIVE_INFINITY;
      case "-Inf" -> Double.NEGATIVE_INFINITY;
      default -> Double.parseDouble(value);
    };
  }

  public record Sample(String name, Map<String, String> labels, double value) {

    public String label(String name) {
      return labels.getOrDefault(name, "");
    }

    public long longValue() {
      return Double.isFinite(value) ? (long) value : 0;
    }
  }
}
