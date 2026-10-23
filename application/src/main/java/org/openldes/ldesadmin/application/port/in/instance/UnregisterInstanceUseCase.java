package org.openldes.ldesadmin.application.port.in.instance;

import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

/**
 * Removes an instance from LDES-admin. A local instance is stopped as well.
 */
public interface UnregisterInstanceUseCase {

  /**
   * @throws InstanceNotFoundException if there is no instance of the given type with that id
   */
  void unregister(InstanceType type, InstanceId id);
}
