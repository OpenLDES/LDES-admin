package org.openldes.ldesadmin.application.service.sandbox;

import java.net.URI;
import java.util.Arrays;
import org.openldes.ldesadmin.application.port.in.sandbox.PollUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.sandbox.HttpPoller;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.sandbox.Poll;
import org.openldes.ldesadmin.model.sandbox.PolledContent;

@ApplicationService
public class PollService implements PollUseCase {

  private final InstanceRepository instanceRepository;
  private final HttpPoller httpPoller;

  public PollService(InstanceRepository instanceRepository, HttpPoller httpPoller) {
    this.instanceRepository = instanceRepository;
    this.httpPoller = httpPoller;
  }

  @Override
  public PolledContent poll(Poll poll) {
    return httpPoller.poll(poll.withUrl(reachable(poll.url())));
  }

  /**
   * A pipeline on a local LDIO polls the other local instances on their internal URL, e.g.
   * {@code http://sandbox-ldes-server:8080/sandbox-parcels}, which only resolves in their Docker
   * network.
   */
  private URI reachable(URI url) {
    return Arrays.stream(InstanceType.values())
        .flatMap(type -> instanceRepository.findByType(type).stream())
        .filter(Instance::isLocal)
        .map(instance -> instance.reachable(url.toString()))
        .filter(translated -> !translated.equals(url))
        .findFirst()
        .orElse(url);
  }
}
