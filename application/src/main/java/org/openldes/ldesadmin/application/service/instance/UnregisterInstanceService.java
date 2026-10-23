package org.openldes.ldesadmin.application.service.instance;

import org.openldes.ldesadmin.application.port.in.instance.UnregisterInstanceUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

@ApplicationService
public class UnregisterInstanceService implements UnregisterInstanceUseCase {

  private final InstanceLookup instanceLookup;
  private final InstanceRepository instanceRepository;
  private final LocalInstanceRuntime localInstanceRuntime;

  public UnregisterInstanceService(InstanceLookup instanceLookup,
      InstanceRepository instanceRepository, LocalInstanceRuntime localInstanceRuntime) {
    this.instanceLookup = instanceLookup;
    this.instanceRepository = instanceRepository;
    this.localInstanceRuntime = localInstanceRuntime;
  }

  @Override
  public void unregister(InstanceType type, InstanceId id) {
    Instance instance = instanceLookup.require(type, id);
    if (instance.isLocal()) {
      localInstanceRuntime.stop(instance.runtimeReference());
    }
    instanceRepository.deleteById(id);
  }
}
