package org.openldes.ldesadmin.application.port.in.sandbox;

import org.openldes.ldesadmin.application.port.out.runtime.LocalRuntimeException;
import org.openldes.ldesadmin.model.sandbox.Sandbox;

/**
 * Starts and stops a local LDES server and LDIO that work together out of the box: the LDIO
 * reaches the LDES server, which has an event stream to experiment with, and the LDIO has a
 * pipeline that feeds it.
 */
public interface SandboxUseCase {

  Sandbox get();

  /**
   * Starts what does not run yet, and prepares it. Returns once both accept requests.
   *
   * @throws LocalRuntimeException if an instance could not be started
   */
  Sandbox start();

  /**
   * Stops and removes the sandbox LDES server and LDIO, with everything in them.
   */
  void stop();
}
