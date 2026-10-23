package org.openldes.ldesadmin.application.port.in.instance;

import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

public class InstanceNotFoundException extends RuntimeException {

  public InstanceNotFoundException(InstanceType type, InstanceId id) {
    super("No %s found with id %s".formatted(type, id));
  }
}
