package org.openldes.ldesadmin.application.service.ldes;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.ldes.ViewUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.View;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

@ApplicationService
public class ViewService implements ViewUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;

  public ViewService(InstanceLookup instanceLookup, LdesServerClient ldesServerClient) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
  }

  @Override
  public List<View> list(InstanceId serverId, String collection) {
    return ldesServerClient.getViews(instanceLookup.requireLdesServer(serverId), collection);
  }

  @Override
  public View get(InstanceId serverId, String collection, String view) {
    return ldesServerClient.getView(instanceLookup.requireLdesServer(serverId), collection, view);
  }

  @Override
  public View create(InstanceId serverId, String collection, RdfDocument definition) {
    return ldesServerClient.createView(instanceLookup.requireLdesServer(serverId), collection,
        definition);
  }

  @Override
  public void delete(InstanceId serverId, String collection, String view) {
    ldesServerClient.deleteView(instanceLookup.requireLdesServer(serverId), collection, view);
  }
}
