package org.openldes.ldesadmin.application.service.ldes;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.ldes.BackgroundProcessUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.BackgroundProcess;

@ApplicationService
public class BackgroundProcessService implements BackgroundProcessUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;

  public BackgroundProcessService(
      InstanceLookup instanceLookup, LdesServerClient ldesServerClient) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
  }

  @Override
  public List<BackgroundProcess> processes(InstanceId serverId) {
    return ldesServerClient.getBackgroundProcesses(instanceLookup.requireLdesServer(serverId));
  }
}
