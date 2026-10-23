package org.openldes.ldesadmin.application.service.instance;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.instance.FindInstancesUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

@ApplicationService
public class FindInstancesService implements FindInstancesUseCase {

  private final InstanceRepository instanceRepository;
  private final InstanceLookup instanceLookup;

  public FindInstancesService(InstanceRepository instanceRepository,
      InstanceLookup instanceLookup) {
    this.instanceRepository = instanceRepository;
    this.instanceLookup = instanceLookup;
  }

  @Override
  public List<Instance> findAll(InstanceType type) {
    return instanceRepository.findByType(type);
  }

  @Override
  public Instance get(InstanceType type, InstanceId id) {
    return instanceLookup.require(type, id);
  }
}
