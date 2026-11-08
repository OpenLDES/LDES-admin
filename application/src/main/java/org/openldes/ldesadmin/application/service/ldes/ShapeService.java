package org.openldes.ldesadmin.application.service.ldes;

import org.openldes.ldesadmin.application.port.in.ldes.ShapeUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

@ApplicationService
public class ShapeService implements ShapeUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;

  public ShapeService(InstanceLookup instanceLookup, LdesServerClient ldesServerClient) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
  }

  @Override
  public RdfDocument get(InstanceId serverId, String collection) {
    return ldesServerClient.getShape(instanceLookup.requireLdesServer(serverId), collection);
  }

  @Override
  public RdfDocument update(InstanceId serverId, String collection, RdfDocument shape) {
    return ldesServerClient.updateShape(
        instanceLookup.requireLdesServer(serverId), collection, shape);
  }
}
