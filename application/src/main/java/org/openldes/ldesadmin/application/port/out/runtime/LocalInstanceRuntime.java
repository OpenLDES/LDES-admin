package org.openldes.ldesadmin.application.port.out.runtime;

import java.net.URI;
import org.openldes.ldesadmin.model.instance.InstanceType;

/**
 * Starts and stops LDES servers and LDIOs on the machine running LDES-admin.
 */
public interface LocalInstanceRuntime {

  /**
   * Starts a new instance and returns once it accepts requests.
   *
   * @throws LocalRuntimeException if the instance could not be started
   */
  LocalRuntimeHandle start(InstanceType type, String name);

  /**
   * Stops an instance and releases everything it used. Does nothing if it is not running
   * anymore.
   */
  void stop(String runtimeReference);

  /**
   * @param url              base URL on which the started instance can be reached
   * @param runtimeReference reference needed to {@link #stop(String) stop} the instance again
   */
  record LocalRuntimeHandle(URI url, String runtimeReference) {

  }
}
