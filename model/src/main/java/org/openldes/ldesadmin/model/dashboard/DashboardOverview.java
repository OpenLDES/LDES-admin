package org.openldes.ldesadmin.model.dashboard;

import java.util.List;

/** Everything the dashboard shows: the health and activity of every managed instance. */
public record DashboardOverview(List<LdesServerSummary> ldesServers, List<LdioSummary> ldios) {

  public DashboardOverview {
    ldesServers = ldesServers == null ? List.of() : List.copyOf(ldesServers);
    ldios = ldios == null ? List.of() : List.copyOf(ldios);
  }
}
