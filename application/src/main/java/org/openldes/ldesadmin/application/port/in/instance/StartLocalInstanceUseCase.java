package org.openldes.ldesadmin.application.port.in.instance;

import java.util.Objects;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;

/**
 * Starts a new LDES server or LDIO on the machine running LDES-admin. Stopping it is done by {@link
 * UnregisterInstanceUseCase unregistering} it.
 */
public interface StartLocalInstanceUseCase {

  Instance start(StartLocalInstanceCommand command);

  /**
   * @param sandbox whether the instance is meant for experimenting in the sandbox
   */
  record StartLocalInstanceCommand(InstanceType type, String name, boolean sandbox) {

    public StartLocalInstanceCommand {
      Objects.requireNonNull(type, "'type' must not be null");
    }
  }
}
