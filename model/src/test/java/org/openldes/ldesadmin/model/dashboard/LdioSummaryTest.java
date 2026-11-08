package org.openldes.ldesadmin.model.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.openldes.ldesadmin.model.ldio.StatusChangeSource;

class LdioSummaryTest {

  private static final Instant NOW = Instant.parse("2026-10-23T10:15:30Z");

  @Test
  void countsPipelinesByStatusAndSumsProcessedMembers() {
    Instance ldio =
        Instance.register(
            InstanceType.LDIO, "ldio", URI.create("http://localhost:8082"), false, NOW);
    LdioSummary summary =
        new LdioSummary(
            ldio,
            InstanceHealth.up(NOW),
            List.of(
                deployed("a", PipelineStatus.RUNNING),
                deployed("b", PipelineStatus.RUNNING),
                deployed("c", PipelineStatus.HALTED)),
            List.of(new PipelineStatistics("a", 10, 8), new PipelineStatistics("b", 5, 5)),
            List.of());

    assertThat(summary.pipelinesByStatus())
        .containsEntry(PipelineStatus.RUNNING, 2L)
        .containsEntry(PipelineStatus.HALTED, 1L)
        .doesNotContainKey(PipelineStatus.STOPPED);
    assertThat(summary.processedMembers()).isEqualTo(13);
  }

  private static DeployedPipeline deployed(String name, PipelineStatus status) {
    Pipeline pipeline =
        new Pipeline(name, null, new InputDefinition("Ldio:HttpIn", null, null), null, null);
    return new DeployedPipeline(pipeline, status, StatusChangeSource.AUTO);
  }
}
