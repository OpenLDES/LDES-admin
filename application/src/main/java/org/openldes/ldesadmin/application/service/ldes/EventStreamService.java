package org.openldes.ldesadmin.application.service.ldes;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.ldes.EventStreamUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

@ApplicationService
public class EventStreamService implements EventStreamUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;

  public EventStreamService(InstanceLookup instanceLookup, LdesServerClient ldesServerClient) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
  }

  @Override
  public List<EventStream> list(InstanceId serverId) {
    return ldesServerClient.getEventStreams(instanceLookup.requireLdesServer(serverId));
  }

  @Override
  public EventStream get(InstanceId serverId, String collection) {
    return ldesServerClient.getEventStream(instanceLookup.requireLdesServer(serverId),
        collection);
  }

  @Override
  public EventStream create(InstanceId serverId, RdfDocument definition) {
    return ldesServerClient.createEventStream(instanceLookup.requireLdesServer(serverId),
        definition);
  }

  @Override
  public void delete(InstanceId serverId, String collection) {
    ldesServerClient.deleteEventStream(instanceLookup.requireLdesServer(serverId), collection);
  }
}
