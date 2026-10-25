package org.openldes.ldesadmin.model.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.openldes.ldesadmin.model.instance.InstanceId;

/**
 * Members an LDES server ingested per hour.
 *
 * @param points one per hour, oldest first; hours before the first sample are left out
 */
public record ThroughputSeries(InstanceId instanceId, String label, List<Point> points) {

  public ThroughputSeries {
    Objects.requireNonNull(instanceId, "'instanceId' must not be null");
    points = points == null ? List.of() : List.copyOf(points);
  }

  /**
   * @param hour start of the hour
   */
  public record Point(Instant hour, long members) {

  }

  /**
   * Derives the members ingested per hour, over the given number of hours up to now, from samples
   * of the ingested total. A total that dropped (e.g. because members were deleted) counts as no
   * ingestion.
   */
  public static ThroughputSeries fromSamples(InstanceId instanceId, String label,
      List<IngestSample> samples, Instant now, int hours) {
    List<IngestSample> sorted = samples.stream()
        .filter(sample -> sample.instanceId().equals(instanceId))
        .sorted(Comparator.comparing(IngestSample::time))
        .toList();
    List<Point> points = new ArrayList<>();
    if (sorted.isEmpty()) {
      return new ThroughputSeries(instanceId, label, points);
    }
    Instant firstHour = now.truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(hours - 1L));
    for (int i = 0; i < hours; i++) {
      Instant start = firstHour.plus(Duration.ofHours(i));
      Instant end = start.plus(Duration.ofHours(1));
      Optional<IngestSample> last = lastBefore(sorted, end);
      if (last.isEmpty()) {
        continue;
      }
      // In the hour sampling started, count from the first sample
      long before = lastBefore(sorted, start).orElse(sorted.getFirst()).ingestedMembers();
      points.add(new Point(start, Math.max(0, last.get().ingestedMembers() - before)));
    }
    return new ThroughputSeries(instanceId, label, points);
  }

  private static Optional<IngestSample> lastBefore(List<IngestSample> sorted, Instant moment) {
    return sorted.stream().filter(sample -> sample.time().isBefore(moment)).reduce((a, b) -> b);
  }
}
