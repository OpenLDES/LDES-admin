package org.openldes.ldesadmin.adapter.out.ldes;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.openldes.ldesadmin.adapter.out.http.PrometheusMetrics;
import org.openldes.ldesadmin.adapter.out.http.PrometheusMetrics.Sample;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldes.ViewStatistics;

/**
 * Reads the member counts the LDES server publishes as gauges.
 */
final class LdesStatisticsReader {

  static final String INGESTED = "ldes_server_ingested_members_count";
  static final String BUCKETISED = "ldes_server_bucket_members_count";
  static final String PAGINATED = "ldes_server_pagination_members_count";

  private LdesStatisticsReader() {
  }

  static List<StreamStatistics> read(PrometheusMetrics metrics) {
    Map<String, Long> ingested = new TreeMap<>();
    metrics.samples(INGESTED)
        .forEach(sample -> ingested.merge(sample.label("collection"), sample.longValue(),
            Long::sum));
    Map<String, Map<String, Long>> bucketised = countPerView(metrics, BUCKETISED);
    Map<String, Map<String, Long>> paginated = countPerView(metrics, PAGINATED);

    TreeSet<String> collections = new TreeSet<>(ingested.keySet());
    collections.addAll(bucketised.keySet());
    collections.addAll(paginated.keySet());
    return collections.stream()
        .map(collection -> {
          TreeSet<String> views = new TreeSet<>(bucketised.getOrDefault(collection, Map.of())
              .keySet());
          views.addAll(paginated.getOrDefault(collection, Map.of()).keySet());
          List<ViewStatistics> viewStatistics = views.stream()
              .map(view -> new ViewStatistics(view,
                  bucketised.getOrDefault(collection, Map.of()).getOrDefault(view, 0L),
                  paginated.getOrDefault(collection, Map.of()).getOrDefault(view, 0L)))
              .toList();
          return new StreamStatistics(collection, ingested.getOrDefault(collection, 0L),
              viewStatistics);
        })
        .toList();
  }

  private static Map<String, Map<String, Long>> countPerView(PrometheusMetrics metrics,
      String name) {
    Map<String, Map<String, Long>> counts = new TreeMap<>();
    metrics.samples(name).forEach(sample -> counts
        .computeIfAbsent(sample.label("collection"), _ -> new TreeMap<>())
        .merge(viewName(sample), sample.longValue(), Long::sum));
    return counts;
  }

  /**
   * Views are labelled by name, possibly prefixed with their collection.
   */
  private static String viewName(Sample sample) {
    String view = sample.label("view");
    String prefix = sample.label("collection") + "/";
    return view.startsWith(prefix) ? view.substring(prefix.length()) : view;
  }
}
