package org.openldes.ldesadmin.model.dashboard;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;

/**
 * Dashboard view of an LDIO.
 *
 * @param pipelines pipelines deployed on the LDIO; empty when the LDIO could not be queried
 * @param statistics throughput per pipeline
 * @param problems what went wrong while querying the LDIO
 */
public record LdioSummary(
    Instance ldio,
    InstanceHealth health,
    List<DeployedPipeline> pipelines,
    List<PipelineStatistics> statistics,
    List<String> problems) {

  public LdioSummary {
    Objects.requireNonNull(ldio, "'ldio' must not be null");
    Objects.requireNonNull(health, "'health' must not be null");
    pipelines = pipelines == null ? List.of() : List.copyOf(pipelines);
    statistics = statistics == null ? List.of() : List.copyOf(statistics);
    problems = problems == null ? List.of() : List.copyOf(problems);
  }

  public Map<PipelineStatus, Long> pipelinesByStatus() {
    Map<PipelineStatus, Long> counts = new EnumMap<>(PipelineStatus.class);
    pipelines.forEach(pipeline -> counts.merge(pipeline.status(), 1L, Long::sum));
    return counts;
  }

  public long processedMembers() {
    return statistics.stream().mapToLong(PipelineStatistics::membersOut).sum();
  }
}
