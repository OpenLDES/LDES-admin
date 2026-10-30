package org.openldes.ldesadmin.application.service.instance;

import org.openldes.ldesadmin.application.port.in.instance.InstanceNotFoundException;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

/** Looks up registered instances for the services that act on them. */
@ApplicationService
public class InstanceLookup {

  private final InstanceRepository instanceRepository;

  public InstanceLookup(InstanceRepository instanceRepository) {
    this.instanceRepository = instanceRepository;
  }

  /**
   * @throws InstanceNotFoundException if there is no instance of the given type with that id
   */
  public Instance require(InstanceType type, InstanceId id) {
    return instanceRepository
        .findById(id)
        .filter(instance -> instance.type() == type)
        .orElseThrow(() -> new InstanceNotFoundException(type, id));
  }

  public Instance requireLdesServer(InstanceId id) {
    return require(InstanceType.LDES_SERVER, id);
  }

  public Instance requireLdio(InstanceId id) {
    return require(InstanceType.LDIO, id);
  }
}
