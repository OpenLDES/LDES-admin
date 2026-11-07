package org.openldes.ldesadmin.adapter.in.rest.dashboard;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.openldes.ldesadmin.adapter.in.rest.instance.InstanceHealthWebModel;
import org.openldes.ldesadmin.adapter.in.rest.instance.InstanceWebModel;
import org.openldes.ldesadmin.adapter.in.rest.ldes.StreamStatisticsWebModel;
import org.openldes.ldesadmin.model.dashboard.DashboardOverview;
import org.openldes.ldesadmin.model.dashboard.LdesServerSummary;
import org.openldes.ldesadmin.model.dashboard.LdioSummary;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;

public record DashboardWebModel(
    List<LdesServerSummaryWebModel> ldesServers, List<LdioSummaryWebModel> ldios) {

  public static DashboardWebModel fromDomain(DashboardOverview overview) {
    return new DashboardWebModel(
        overview.ldesServers().stream().map(LdesServerSummaryWebModel::fromDomain).toList(),
        overview.ldios().stream().map(LdioSummaryWebModel::fromDomain).toList());
  }

  public record LdesServerSummaryWebModel(
      InstanceWebModel server,
      InstanceHealthWebModel health,
      long ingestedMembers,
      List<StreamStatisticsWebModel> streams,
      List<String> problems) {

    static LdesServerSummaryWebModel fromDomain(LdesServerSummary summary) {
      return new LdesServerSummaryWebModel(
          InstanceWebModel.fromDomain(summary.server()),
          InstanceHealthWebModel.fromDomain(summary.health()),
          summary.ingestedMembers(),
          summary.streams().stream().map(StreamStatisticsWebModel::fromDomain).toList(),
          summary.problems());
    }
  }

  public record LdioSummaryWebModel(
      InstanceWebModel ldio,
      InstanceHealthWebModel health,
      Map<PipelineStatus, Long> pipelinesByStatus,
      long processedMembers,
      List<PipelineSummaryWebModel> pipelines,
      List<String> problems) {

    static LdioSummaryWebModel fromDomain(LdioSummary summary) {
      Map<String, PipelineStatistics> statistics =
          summary.statistics().stream()
              .collect(Collectors.toMap(PipelineStatistics::pipeline, Function.identity()));
      List<PipelineSummaryWebModel> pipelines =
          summary.pipelines().stream()
              .map(
                  pipeline -> {
                    PipelineStatistics stats = statistics.get(pipeline.name());
                    return new PipelineSummaryWebModel(
                        pipeline.name(),
                        pipeline.status(),
                        stats == null ? 0 : stats.membersIn(),
                        stats == null ? 0 : stats.membersOut());
                  })
              .toList();
      return new LdioSummaryWebModel(
          InstanceWebModel.fromDomain(summary.ldio()),
          InstanceHealthWebModel.fromDomain(summary.health()),
          summary.pipelinesByStatus(),
          summary.processedMembers(),
          pipelines,
          summary.problems());
    }
  }

  public record PipelineSummaryWebModel(
      String name, PipelineStatus status, long membersIn, long membersOut) {}
}
