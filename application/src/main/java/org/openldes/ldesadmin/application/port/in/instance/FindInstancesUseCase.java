package org.openldes.ldesadmin.application.port.in.instance;

import java.util.List;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

public interface FindInstancesUseCase {

  /**
   * All instances of the given type, ordered by name.
   */
  List<Instance> findAll(InstanceType type);

  /**
   * @throws InstanceNotFoundException if there is no instance of the given type with that id
   */
  Instance get(InstanceType type, InstanceId id);
}
