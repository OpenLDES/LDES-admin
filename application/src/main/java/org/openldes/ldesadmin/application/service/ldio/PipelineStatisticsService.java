package org.openldes.ldesadmin.application.service.ldio;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineStatisticsUseCase;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;

@ApplicationService
public class PipelineStatisticsService implements PipelineStatisticsUseCase {

  private final InstanceLookup instanceLookup;
  private final LdioClient ldioClient;

  public PipelineStatisticsService(InstanceLookup instanceLookup, LdioClient ldioClient) {
    this.instanceLookup = instanceLookup;
    this.ldioClient = ldioClient;
  }

  @Override
  public List<PipelineStatistics> statistics(InstanceId ldioId) {
    return ldioClient.getStatistics(instanceLookup.requireLdio(ldioId));
  }
}
