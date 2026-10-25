package org.openldes.ldesadmin.application.service.ldes;

import org.openldes.ldesadmin.application.port.in.ldes.DcatUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException.Reason;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
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
  public CatalogDescription getServerCatalog(InstanceId serverId) {
    return ldesServerClient.getServerCatalog(instanceLookup.requireLdesServer(serverId));
  }

  @Override
  public String saveServerCatalog(InstanceId serverId, RdfDocument catalog) {
    Instance server = instanceLookup.requireLdesServer(serverId);
    CatalogDescription current = ldesServerClient.getServerCatalog(server);
    if (current.catalogId() != null) {
      ldesServerClient.updateDcat(server, new DcatTarget.ServerCatalog(current.catalogId()),
          catalog);
      return current.catalogId();
    }
    return ldesServerClient.createDcat(server, new DcatTarget.ServerCatalog(null), catalog)
        .orElseThrow(() -> new RemoteInstanceException(Reason.UNAVAILABLE,
            "LDES server '%s' did not return the id of the new catalog".formatted(server.name())));
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
