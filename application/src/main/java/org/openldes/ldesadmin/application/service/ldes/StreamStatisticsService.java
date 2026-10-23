package org.openldes.ldesadmin.application.service.ldes;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.ldes.StreamStatisticsUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;

@ApplicationService
public class StreamStatisticsService implements StreamStatisticsUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;

  public StreamStatisticsService(InstanceLookup instanceLookup,
      LdesServerClient ldesServerClient) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
  }

  @Override
  public List<StreamStatistics> statistics(InstanceId serverId) {
    return ldesServerClient.getStatistics(instanceLookup.requireLdesServer(serverId));
  }
}
