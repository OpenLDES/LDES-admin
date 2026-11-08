package org.openldes.ldesadmin.application.port.in.instance;

import java.net.URI;
import org.openldes.ldesadmin.model.instance.InstanceType;

public class InstanceAlreadyRegisteredException extends RuntimeException {

  public InstanceAlreadyRegisteredException(InstanceType type, URI url) {
    super(
        "%s with URL %s is already registered"
            .formatted(type == InstanceType.LDIO ? "An LDIO" : "An LDES server", url));
  }
}
