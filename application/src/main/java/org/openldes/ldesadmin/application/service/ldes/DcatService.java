package org.openldes.ldesadmin.application.service.ldes;

import org.openldes.ldesadmin.application.port.in.ldes.DcatUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

@ApplicationService
public class DcatService implements DcatUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;

  public DcatService(InstanceLookup instanceLookup, LdesServerClient ldesServerClient) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
  }

  @Override
  public RdfDocument getCatalog(InstanceId serverId) {
    return ldesServerClient.getCatalog(instanceLookup.requireLdesServer(serverId));
  }

  @Override
  public void create(InstanceId serverId, DcatTarget target, RdfDocument dcat) {
    ldesServerClient.createDcat(instanceLookup.requireLdesServer(serverId), target, dcat);
  }

  @Override
  public void update(InstanceId serverId, DcatTarget target, RdfDocument dcat) {
    requireCatalogId(target);
    ldesServerClient.updateDcat(instanceLookup.requireLdesServer(serverId), target, dcat);
  }

  @Override
  public void delete(InstanceId serverId, DcatTarget target) {
    requireCatalogId(target);
    ldesServerClient.deleteDcat(instanceLookup.requireLdesServer(serverId), target);
  }

  private static void requireCatalogId(DcatTarget target) {
    if (target instanceof DcatTarget.ServerCatalog(String catalogId)
        && (catalogId == null || catalogId.isBlank())) {
      throw new IllegalArgumentException("An existing server catalog is identified by its id");
    }
  }
}
