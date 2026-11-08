package org.openldes.ldesadmin.application.port.out.health;

import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;

/**
 * Determines whether an instance is up. Never throws: an unreachable instance is reported as down.
 */
public interface InstanceHealthProbe {

  InstanceHealth check(Instance instance);
}
