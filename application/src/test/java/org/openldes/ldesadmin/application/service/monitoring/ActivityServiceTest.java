package org.openldes.ldesadmin.application.service.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.out.monitoring.ActivityLog;
import org.openldes.ldesadmin.application.port.out.monitoring.IngestSampleRepository;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;
import org.openldes.ldesadmin.model.monitoring.IngestSample;
import org.openldes.ldesadmin.model.monitoring.ThroughputSeries;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

  @Mock private InstanceRepository instanceRepository;
  @Mock private IngestSampleRepository ingestSamples;
  @Mock private ActivityLog activityLog;

  @Test
  void derivesTheThroughputOfTheSampledServers() {
    Instance sampled = TestFixtures.ldesServer();
    Instance neverSampled = TestFixtures.localLdesServer();
    when(instanceRepository.findByType(InstanceType.LDES_SERVER))
        .thenReturn(List.of(sampled, neverSampled));
    when(ingestSamples.findSince(TestFixtures.NOW.minus(Duration.ofHours(25))))
        .thenReturn(
            List.of(
                new IngestSample(sampled.id(), TestFixtures.NOW.minus(Duration.ofMinutes(90)), 100),
                new IngestSample(
                    sampled.id(), TestFixtures.NOW.minus(Duration.ofMinutes(1)), 160)));

    List<ThroughputSeries> throughput = service().throughput();

    assertThat(throughput)
        .singleElement()
        .satisfies(
            series -> {
              assertThat(series.label()).isEqualTo("LDES server");
              // Sampling started at 8:45, the second sample is from the current hour
              assertThat(series.points())
                  .extracting(ThroughputSeries.Point::members)
                  .containsExactly(0L, 0L, 60L);
            });
  }

  @Test
  void limitsTheRecentActivity() {
    List<ActivityEvent> events =
        List.of(
            ActivityEvent.of(
                TestFixtures.ldio(), Severity.INFO, "LDIO added", null, TestFixtures.NOW));
    when(activityLog.findRecent(ActivityService.MAX_ACTIVITY)).thenReturn(events);

    assertThat(service().recentActivity(10_000)).isEqualTo(events);
    verify(activityLog).findRecent(ActivityService.MAX_ACTIVITY);
  }

  @Test
  void needsAPositiveLimit() {
    ActivityService service = service();

    assertThatThrownBy(() -> service.recentActivity(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ActivityService service() {
    return new ActivityService(instanceRepository, ingestSamples, activityLog, TestFixtures.CLOCK);
  }
}
