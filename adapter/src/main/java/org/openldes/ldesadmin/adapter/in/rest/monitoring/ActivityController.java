package org.openldes.ldesadmin.adapter.in.rest.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.openldes.ldesadmin.application.port.in.monitoring.ActivityUseCase;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;
import org.openldes.ldesadmin.model.monitoring.ThroughputSeries;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What the instances did lately, as far as LDES-admin noticed. */
@RestController
@RequestMapping("/api/v1")
class ActivityController {

  private final ActivityUseCase activityUseCase;

  ActivityController(ActivityUseCase activityUseCase) {
    this.activityUseCase = activityUseCase;
  }

  /** The latest activity, newest first. */
  @GetMapping("/activity")
  List<ActivityEventWebModel> activity(@RequestParam(defaultValue = "50") int limit) {
    return activityUseCase.recentActivity(limit).stream()
        .map(ActivityEventWebModel::fromDomain)
        .toList();
  }

  /** Members ingested per hour by the LDES servers, over the last 24 hours. */
  @GetMapping("/dashboard/throughput")
  List<ThroughputSeriesWebModel> throughput() {
    return activityUseCase.throughput().stream().map(ThroughputSeriesWebModel::fromDomain).toList();
  }

  /**
   * @param severity {@code info}, {@code success}, {@code warn} or {@code error}
   */
  record ActivityEventWebModel(
      UUID id, Instant time, String severity, Source source, String title, String detail) {

    static ActivityEventWebModel fromDomain(ActivityEvent event) {
      return new ActivityEventWebModel(
          event.id(),
          event.time(),
          event.severity().name().toLowerCase(Locale.ROOT),
          new Source(event.sourceType(), event.sourceId().value(), event.sourceName()),
          event.title(),
          event.detail());
    }

    /**
     * @param name name of the instance when it happened
     */
    record Source(InstanceType type, UUID id, String name) {}
  }

  record ThroughputSeriesWebModel(UUID instanceId, String label, List<Point> points) {

    static ThroughputSeriesWebModel fromDomain(ThroughputSeries series) {
      return new ThroughputSeriesWebModel(
          series.instanceId().value(),
          series.label(),
          series.points().stream().map(point -> new Point(point.hour(), point.members())).toList());
    }

    /**
     * @param time start of the hour
     */
    record Point(Instant time, long members) {}
  }
}
