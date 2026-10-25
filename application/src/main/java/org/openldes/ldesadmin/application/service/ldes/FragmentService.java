package org.openldes.ldesadmin.application.service.ldes;

import java.net.URI;
import org.openldes.ldesadmin.application.port.in.ldes.FragmentUseCase;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.Fragment;

@ApplicationService
public class FragmentService implements FragmentUseCase {

  private final InstanceLookup instanceLookup;
  private final LdesServerClient ldesServerClient;

  public FragmentService(InstanceLookup instanceLookup, LdesServerClient ldesServerClient) {
    this.instanceLookup = instanceLookup;
    this.ldesServerClient = ldesServerClient;
  }

  @Override
  public Fragment fetch(InstanceId serverId, String collection, String view, String url) {
    Instance server = instanceLookup.requireLdesServer(serverId);
    EventStream eventStream = ldesServerClient.getEventStream(server, collection);
    URI location = url == null || url.isBlank()
        ? server.resolve(collection + "/" + view)
        : server.reachable(url);
    // LDES-admin fetches on behalf of the user, so it must not become a proxy to anywhere else
    if (!server.serves(location)) {
      throw new IllegalArgumentException(
          "%s is not a fragment of LDES server '%s'".formatted(url, server.name()));
    }
    return ldesServerClient.getFragment(server, location, eventStream.timestampPath(),
        eventStream.versionOfPath());
  }
}
