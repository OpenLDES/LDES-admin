package org.openldes.ldesadmin.application.service.ldes;

import org.openldes.ldesadmin.application.port.in.ldes.IngestMemberUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

@ApplicationService
public class IngestMemberService implements IngestMemberUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;

  public IngestMemberService(InstanceLookup instanceLookup, LdesServerClient ldesServerClient) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
  }

  @Override
  public void ingest(InstanceId serverId, String collection, RdfDocument member) {
    if (member.content().isBlank()) {
      throw new IllegalArgumentException("A member must not be empty");
    }
    ldesServerClient.ingest(instanceLookup.requireLdesServer(serverId), collection, member);
  }
}
