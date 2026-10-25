package org.openldes.ldesadmin.application.port.out.runtime;

import java.net.URI;
import org.openldes.ldesadmin.model.instance.InstanceType;

/**
 * Starts and stops LDES servers and LDIOs on the machine running LDES-admin. They share a network,
 * so local instances reach each other on their internal URL.
 */
public interface LocalInstanceRuntime {

  /**
   * Starts a new instance and returns once it accepts requests.
   *
   * @param name name of the instance; it decides the host name other local instances use
   * @throws LocalRuntimeException if the instance could not be started
   */
  LocalRuntimeHandle start(InstanceType type, String name);

  /**
   * Stops an instance and releases everything it used. Does nothing if it is not running
   * anymore.
   */
  void stop(String runtimeReference);

  /**
   * Whether an instance started earlier still runs. Local instances stop with the LDES-admin run
   * that started them.
   */
  boolean isRunning(String runtimeReference);

  /**
   * @param url              base URL on which LDES-admin reaches the started instance
   * @param internalUrl      base URL on which other local instances reach it
   * @param runtimeReference reference needed to {@link #stop(String) stop} the instance again
   */
  record LocalRuntimeHandle(URI url, URI internalUrl, String runtimeReference) {

  }
}
