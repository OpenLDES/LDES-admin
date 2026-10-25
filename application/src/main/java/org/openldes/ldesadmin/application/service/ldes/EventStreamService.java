package org.openldes.ldesadmin.application.service.ldes;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.ldes.EventStreamUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.application.service.monitoring.ActivityRecorder;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

@ApplicationService
public class EventStreamService implements EventStreamUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;
  private final ActivityRecorder activity;

  public EventStreamService(InstanceLookup instanceLookup, LdesServerClient ldesServerClient,
      ActivityRecorder activity) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
    this.activity = activity;
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
    Instance server = instanceLookup.requireLdesServer(serverId);
    EventStream created = ldesServerClient.createEventStream(server, definition);
    activity.success(server, "Event stream " + created.collection() + " created");
    return created;
  }

  @Override
  public void delete(InstanceId serverId, String collection) {
    Instance server = instanceLookup.requireLdesServer(serverId);
    ldesServerClient.deleteEventStream(server, collection);
    activity.record(server, Severity.WARN, "Event stream " + collection + " deleted", null);
  }
}
