package org.openldes.ldesadmin.application.service.ldio;

import org.openldes.ldesadmin.application.port.in.ldio.ComponentCatalogUseCase;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.ComponentCatalog;

@ApplicationService
public class ComponentCatalogService implements ComponentCatalogUseCase {

  private final InstanceLookup instanceLookup;
  private final LdioClient ldioClient;

  public ComponentCatalogService(InstanceLookup instanceLookup, LdioClient ldioClient) {
    this.instanceLookup = instanceLookup;
    this.ldioClient = ldioClient;
  }

  @Override
  public ComponentCatalog catalog(InstanceId ldioId) {
    return ldioClient.getCatalog(instanceLookup.requireLdio(ldioId));
  }
}
