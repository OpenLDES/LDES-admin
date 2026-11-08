package org.openldes.ldesadmin.application.service.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.openldes.ldesadmin.application.port.in.monitoring.ActivityUseCase;
import org.openldes.ldesadmin.application.port.out.monitoring.ActivityLog;
import org.openldes.ldesadmin.application.port.out.monitoring.IngestSampleRepository;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;
import org.openldes.ldesadmin.model.monitoring.IngestSample;
import org.openldes.ldesadmin.model.monitoring.ThroughputSeries;

@ApplicationService
public class ActivityService implements ActivityUseCase {

  static final int THROUGHPUT_HOURS = 24;
  static final int MAX_ACTIVITY = 200;

  private final InstanceRepository instanceRepository;
  private final IngestSampleRepository ingestSamples;
  private final ActivityLog activityLog;
  private final Clock clock;

  public ActivityService(
      InstanceRepository instanceRepository,
      IngestSampleRepository ingestSamples,
      ActivityLog activityLog,
      Clock clock) {
    this.instanceRepository = instanceRepository;
    this.ingestSamples = ingestSamples;
    this.activityLog = activityLog;
    this.clock = clock;
  }

  @Override
  public List<ThroughputSeries> throughput() {
    Instant now = clock.instant();
    // One hour extra, to know the total at the start of the first hour
    List<IngestSample> samples =
        ingestSamples.findSince(now.minus(Duration.ofHours(THROUGHPUT_HOURS + 1L)));
    return instanceRepository.findByType(InstanceType.LDES_SERVER).stream()
        .map(
            server ->
                ThroughputSeries.fromSamples(
                    server.id(), server.name(), samples, now, THROUGHPUT_HOURS))
        .filter(series -> !series.points().isEmpty())
        .toList();
  }

  @Override
  public List<ActivityEvent> recentActivity(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("'limit' must be at least 1, but was " + limit);
    }
    return activityLog.findRecent(Math.min(limit, MAX_ACTIVITY));
  }
}
