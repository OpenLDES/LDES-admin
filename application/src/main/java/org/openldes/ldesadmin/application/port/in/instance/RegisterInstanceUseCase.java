package org.openldes.ldesadmin.application.port.in.instance;

import java.net.URI;
import java.util.Objects;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;

/**
 * Registers an LDES server or LDIO that already runs somewhere.
 */
public interface RegisterInstanceUseCase {

  /**
   * @throws InstanceAlreadyRegisteredException if an instance of the same type with the same URL
   *                                            is already registered
   */
  Instance register(RegisterInstanceCommand command);

  record RegisterInstanceCommand(InstanceType type, String name, URI url, boolean sandbox) {

    public RegisterInstanceCommand {
      Objects.requireNonNull(type, "'type' must not be null");
      Objects.requireNonNull(url, "'url' must not be null");
    }
  }
}
