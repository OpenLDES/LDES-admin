package org.openldes.ldesadmin.application.port.in.instance;

import java.net.URI;
import org.openldes.ldesadmin.model.instance.InstanceType;

public class InstanceAlreadyRegisteredException extends RuntimeException {

  public InstanceAlreadyRegisteredException(InstanceType type, URI url) {
    super("A %s with URL %s is already registered".formatted(type, url));
  }
}
