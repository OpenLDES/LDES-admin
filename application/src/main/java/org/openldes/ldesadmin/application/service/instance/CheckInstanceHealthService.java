package org.openldes.ldesadmin.application.service.instance;

import org.openldes.ldesadmin.application.port.in.instance.CheckInstanceHealthUseCase;
import org.openldes.ldesadmin.application.port.out.health.InstanceHealthProbe;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

@ApplicationService
public class CheckInstanceHealthService implements CheckInstanceHealthUseCase {

  private final InstanceLookup instanceLookup;
  private final InstanceHealthProbe instanceHealthProbe;

  public CheckInstanceHealthService(InstanceLookup instanceLookup,
      InstanceHealthProbe instanceHealthProbe) {
    this.instanceLookup = instanceLookup;
    this.instanceHealthProbe = instanceHealthProbe;
  }

  @Override
  public InstanceHealth check(InstanceType type, InstanceId id) {
    return instanceHealthProbe.check(instanceLookup.require(type, id));
  }
}
