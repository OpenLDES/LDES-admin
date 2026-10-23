package org.openldes.ldesadmin.model.dashboard;

import java.util.List;
import java.util.Objects;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;

/**
 * Dashboard view of an LDES server.
 *
 * @param streams  statistics per event stream; empty when the server could not be queried
 * @param problems what went wrong while querying the server
 */
public record LdesServerSummary(Instance server, InstanceHealth health,
                                List<StreamStatistics> streams, List<String> problems) {

  public LdesServerSummary {
    Objects.requireNonNull(server, "'server' must not be null");
    Objects.requireNonNull(health, "'health' must not be null");
    streams = streams == null ? List.of() : List.copyOf(streams);
    problems = problems == null ? List.of() : List.copyOf(problems);
  }

  public long ingestedMembers() {
    return streams.stream().mapToLong(StreamStatistics::ingestedMembers).sum();
  }
}
