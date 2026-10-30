package org.openldes.ldesadmin.model.ldes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StreamStatisticsTest {

  @Test
  void backlogIsDeterminedByTheSlowestView() {
    StreamStatistics statistics =
        new StreamStatistics(
            "parcels",
            1_000,
            List.of(
                new ViewStatistics("by-page", 1_000, 1_000),
                new ViewStatistics("by-time", 900, 750)));

    assertThat(statistics.backlog()).isEqualTo(150);
  }

  @Test
  void viewsWithRetentionDoNotCountAsBacklog() {
    StreamStatistics statistics =
        new StreamStatistics("hindrances", 10_000, List.of(new ViewStatistics("recent", 800, 800)));

    assertThat(statistics.backlog()).isZero();
  }

  @Test
  void streamWithoutViewsHasNoBacklog() {
    assertThat(new StreamStatistics("parcels", 1_000, null).backlog()).isZero();
  }
}
