package org.openldes.ldesadmin.adapter.out.ldes;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.openldes.ldesadmin.adapter.out.http.PrometheusMetrics;
import org.openldes.ldesadmin.adapter.out.http.PrometheusMetrics.Sample;
import org.openldes.ldesadmin.model.ldes.BackgroundProcess;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldes.ViewStatistics;

/**
 * Reads the member counts the LDES server publishes as gauges, and the Spring Batch metrics of its
 * background jobs.
 */
final class LdesStatisticsReader {

  static final String INGESTED = "ldes_server_ingested_members_count";
  static final String BUCKETISED = "ldes_server_bucket_members_count";
  static final String PAGINATED = "ldes_server_pagination_members_count";
  static final String STEP_RUNS = "spring_batch_step_seconds_count";
  static final String STEP_SECONDS = "spring_batch_step_seconds_sum";
  static final String STEP_LONGEST = "spring_batch_step_seconds_max";
  static final String STEP_ACTIVE = "spring_batch_step_active_seconds_count";

  private static final String JOB_LABEL = "spring_batch_step_job_name";
  private static final String STEP_LABEL = "spring_batch_step_name";
  private static final String STATUS_LABEL = "spring_batch_step_status";

  private LdesStatisticsReader() {}

  static List<StreamStatistics> read(PrometheusMetrics metrics) {
    Map<String, Long> ingested = new TreeMap<>();
    metrics
        .samples(INGESTED)
        .forEach(
            sample -> ingested.merge(sample.label("collection"), sample.longValue(), Long::sum));
    Map<String, Map<String, Long>> bucketised = countPerView(metrics, BUCKETISED);
    Map<String, Map<String, Long>> paginated = countPerView(metrics, PAGINATED);

    TreeSet<String> collections = new TreeSet<>(ingested.keySet());
    collections.addAll(bucketised.keySet());
    collections.addAll(paginated.keySet());
    return collections.stream()
        .map(
            collection -> {
              TreeSet<String> views =
                  new TreeSet<>(bucketised.getOrDefault(collection, Map.of()).keySet());
              views.addAll(paginated.getOrDefault(collection, Map.of()).keySet());
              List<ViewStatistics> viewStatistics =
                  views.stream()
                      .map(
                          view ->
                              new ViewStatistics(
                                  view,
                                  bucketised
                                      .getOrDefault(collection, Map.of())
                                      .getOrDefault(view, 0L),
                                  paginated
                                      .getOrDefault(collection, Map.of())
                                      .getOrDefault(view, 0L)))
                      .toList();
              return new StreamStatistics(
                  collection, ingested.getOrDefault(collection, 0L), viewStatistics);
            })
        .toList();
  }

  /** The steps of the background jobs that ran, or run, since the server started. */
  static List<BackgroundProcess> readBackgroundProcesses(PrometheusMetrics metrics) {
    Map<StepKey, StepMetrics> steps = new TreeMap<>();
    metrics
        .samples(STEP_RUNS)
        .forEach(
            sample -> {
              StepMetrics step = steps.computeIfAbsent(StepKey.of(sample), _ -> new StepMetrics());
              switch (sample.label(STATUS_LABEL)) {
                case "COMPLETED" -> step.completed += sample.longValue();
                case "FAILED" -> step.failed += sample.longValue();
                default -> {
                  // Stopped or abandoned runs neither completed nor failed
                }
              }
            });
    metrics
        .samples(STEP_SECONDS)
        .filter(LdesStatisticsReader::completed)
        .forEach(
            sample ->
                steps.computeIfAbsent(StepKey.of(sample), _ -> new StepMetrics()).seconds +=
                    sample.value());
    metrics
        .samples(STEP_LONGEST)
        .filter(LdesStatisticsReader::completed)
        .forEach(
            sample -> {
              StepMetrics step = steps.computeIfAbsent(StepKey.of(sample), _ -> new StepMetrics());
              step.longestSeconds = Math.max(step.longestSeconds, sample.value());
            });
    metrics
        .samples(STEP_ACTIVE)
        .forEach(
            sample ->
                steps.computeIfAbsent(StepKey.of(sample), _ -> new StepMetrics()).active +=
                    sample.longValue());
    return steps.entrySet().stream()
        .map(
            entry -> {
              StepMetrics step = entry.getValue();
              long average =
                  step.completed == 0 ? 0 : Math.round(step.seconds * 1000 / step.completed);
              return new BackgroundProcess(
                  entry.getKey().job(),
                  entry.getKey().step(),
                  step.completed,
                  step.failed,
                  average,
                  Math.round(step.longestSeconds * 1000),
                  step.active > 0);
            })
        .toList();
  }

  private static boolean completed(Sample sample) {
    return sample.label(STATUS_LABEL).equals("COMPLETED");
  }

  private record StepKey(String job, String step) implements Comparable<StepKey> {

    static StepKey of(Sample sample) {
      return new StepKey(sample.label(JOB_LABEL), sample.label(STEP_LABEL));
    }

    @Override
    public int compareTo(StepKey other) {
      int byJob = job.compareTo(other.job);
      return byJob != 0 ? byJob : step.compareTo(other.step);
    }
  }

  private static final class StepMetrics {

    private long completed;
    private long failed;
    private double seconds;
    private double longestSeconds;
    private long active;
  }

  private static Map<String, Map<String, Long>> countPerView(
      PrometheusMetrics metrics, String name) {
    Map<String, Map<String, Long>> counts = new TreeMap<>();
    metrics
        .samples(name)
        .forEach(
            sample ->
                counts
                    .computeIfAbsent(sample.label("collection"), _ -> new TreeMap<>())
                    .merge(viewName(sample), sample.longValue(), Long::sum));
    return counts;
  }

  /** Views are labelled by name, possibly prefixed with their collection. */
  private static String viewName(Sample sample) {
    String view = sample.label("view");
    String prefix = sample.label("collection") + "/";
    return view.startsWith(prefix) ? view.substring(prefix.length()) : view;
  }
}
