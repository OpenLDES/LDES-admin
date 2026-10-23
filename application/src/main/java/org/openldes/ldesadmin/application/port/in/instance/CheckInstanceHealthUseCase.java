package org.openldes.ldesadmin.application.port.in.instance;

import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

public interface CheckInstanceHealthUseCase {

  /**
   * @throws InstanceNotFoundException if there is no instance of the given type with that id
   */
  InstanceHealth check(InstanceType type, InstanceId id);
}
