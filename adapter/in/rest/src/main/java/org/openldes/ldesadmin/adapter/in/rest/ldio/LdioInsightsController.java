package org.openldes.ldesadmin.adapter.in.rest.ldio;

import java.util.List;
import java.util.UUID;
import org.openldes.ldesadmin.application.port.in.ldio.ComponentCatalogUseCase;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineStatisticsUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.ComponentCatalog;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What an LDIO can do (its component catalog) and what it did (pipeline throughput). */
@RestController
@RequestMapping("/api/v1/ldios/{ldioId}")
class LdioInsightsController {

  private final ComponentCatalogUseCase componentCatalogUseCase;
  private final PipelineStatisticsUseCase pipelineStatisticsUseCase;

  LdioInsightsController(
      ComponentCatalogUseCase componentCatalogUseCase,
      PipelineStatisticsUseCase pipelineStatisticsUseCase) {
    this.componentCatalogUseCase = componentCatalogUseCase;
    this.pipelineStatisticsUseCase = pipelineStatisticsUseCase;
  }

  @GetMapping("/catalog")
  ComponentCatalog catalog(@PathVariable UUID ldioId) {
    return componentCatalogUseCase.catalog(new InstanceId(ldioId));
  }

  @GetMapping("/statistics")
  List<PipelineStatisticsWebModel> statistics(@PathVariable UUID ldioId) {
    return pipelineStatisticsUseCase.statistics(new InstanceId(ldioId)).stream()
        .map(PipelineStatisticsWebModel::fromDomain)
        .toList();
  }

  record PipelineStatisticsWebModel(
      String pipeline, long membersIn, long membersOut, long inFlight) {

    static PipelineStatisticsWebModel fromDomain(PipelineStatistics statistics) {
      return new PipelineStatisticsWebModel(
          statistics.pipeline(),
          statistics.membersIn(),
          statistics.membersOut(),
          statistics.inFlight());
    }
  }
}
