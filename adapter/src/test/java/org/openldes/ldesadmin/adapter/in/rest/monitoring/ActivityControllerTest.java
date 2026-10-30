package org.openldes.ldesadmin.adapter.in.rest.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.AdapterFixtures;
import org.openldes.ldesadmin.application.port.in.monitoring.ActivityUseCase;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;
import org.openldes.ldesadmin.model.monitoring.ThroughputSeries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = ActivityController.class)
class ActivityControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private ActivityUseCase activityUseCase;

  @Test
  void listsTheRecentActivity() {
    ActivityEvent event =
        ActivityEvent.of(
            ldio, Severity.SUCCESS, "Pipeline sandbox-ingest deployed", null, AdapterFixtures.NOW);
    when(activityUseCase.recentActivity(10)).thenReturn(List.of(event));

    assertThat(mvc.get().uri("/api/v1/activity").param("limit", "10"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            [{"time": "2026-10-23T10:15:30Z", "severity": "success",
              "source": {"type": "LDIO", "id": "%s", "name": "Ingest LDIO"},
              "title": "Pipeline sandbox-ingest deployed"}]
            """
                .formatted(ldio.id()));
  }

  @Test
  void reportsTheThroughputPerServer() {
    InstanceId id = server.id();
    when(activityUseCase.throughput())
        .thenReturn(
            List.of(
                new ThroughputSeries(
                    id,
                    "Production",
                    List.of(
                        new ThroughputSeries.Point(Instant.parse("2026-10-23T10:00:00Z"), 42)))));

    assertThat(mvc.get().uri("/api/v1/dashboard/throughput"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            [{"instanceId": "%s", "label": "Production",
              "points": [{"time": "2026-10-23T10:00:00Z", "members": 42}]}]
            """
                .formatted(id));
  }

  private final Instance ldio = AdapterFixtures.ldio();
  private final Instance server = AdapterFixtures.ldesServer();
}
