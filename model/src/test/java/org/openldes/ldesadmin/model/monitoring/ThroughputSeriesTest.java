package org.openldes.ldesadmin.model.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.monitoring.ThroughputSeries.Point;

class ThroughputSeriesTest {

  private static final InstanceId SERVER = InstanceId.randomId();
  private static final Instant NOW = Instant.parse("2026-10-24T12:20:00Z");

  @Test
  void countsTheMembersIngestedPerHour() {
    List<IngestSample> samples =
        List.of(
            sample("2026-10-24T09:10:00Z", 100),
            sample("2026-10-24T09:50:00Z", 160),
            sample("2026-10-24T10:30:00Z", 200),
            sample("2026-10-24T11:59:00Z", 260),
            sample("2026-10-24T12:15:00Z", 270));

    ThroughputSeries series = ThroughputSeries.fromSamples(SERVER, "Production", samples, NOW, 4);

    assertThat(series.points())
        .containsExactly(
            new Point(Instant.parse("2026-10-24T09:00:00Z"), 60),
            new Point(Instant.parse("2026-10-24T10:00:00Z"), 40),
            new Point(Instant.parse("2026-10-24T11:00:00Z"), 60),
            new Point(Instant.parse("2026-10-24T12:00:00Z"), 10));
  }

  @Test
  void leavesOutHoursBeforeSamplingStarted() {
    ThroughputSeries series =
        ThroughputSeries.fromSamples(
            SERVER,
            "Production",
            List.of(sample("2026-10-24T12:01:00Z", 5), sample("2026-10-24T12:11:00Z", 9)),
            NOW,
            24);

    assertThat(series.points())
        .containsExactly(new Point(Instant.parse("2026-10-24T12:00:00Z"), 4));
  }

  @Test
  void countsADroppingTotalAsNoIngestion() {
    ThroughputSeries series =
        ThroughputSeries.fromSamples(
            SERVER,
            "Production",
            List.of(sample("2026-10-24T11:10:00Z", 500), sample("2026-10-24T12:10:00Z", 300)),
            NOW,
            2);

    assertThat(series.points()).extracting(Point::members).containsExactly(0L, 0L);
  }

  @Test
  void ignoresSamplesOfOtherServers() {
    IngestSample other =
        new IngestSample(InstanceId.randomId(), Instant.parse("2026-10-24T12:10:00Z"), 1_000);

    assertThat(ThroughputSeries.fromSamples(SERVER, "Production", List.of(other), NOW, 24).points())
        .isEmpty();
  }

  private static IngestSample sample(String time, long members) {
    return new IngestSample(SERVER, Instant.parse(time), members);
  }
}
