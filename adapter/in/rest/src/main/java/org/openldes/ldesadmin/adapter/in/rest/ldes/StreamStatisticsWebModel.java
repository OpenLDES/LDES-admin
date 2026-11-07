package org.openldes.ldesadmin.adapter.in.rest.ldes;

import java.util.List;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;

public record StreamStatisticsWebModel(
    String collection, long ingestedMembers, long backlog, List<ViewStatisticsWebModel> views) {

  public static StreamStatisticsWebModel fromDomain(StreamStatistics statistics) {
    return new StreamStatisticsWebModel(
        statistics.collection(),
        statistics.ingestedMembers(),
        statistics.backlog(),
        statistics.views().stream()
            .map(
                view ->
                    new ViewStatisticsWebModel(
                        view.view(), view.bucketisedMembers(), view.paginatedMembers()))
            .toList());
  }

  public record ViewStatisticsWebModel(
      String view, long bucketisedMembers, long paginatedMembers) {}
}
