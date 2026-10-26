package org.openldes.ldesadmin.application.port.out.sandbox;

/**
 * A component of a pipeline could not be configured, failed, or took too long. The message says
 * why, for the user who designs the pipeline.
 */
public class ComponentException extends RuntimeException {

  public ComponentException(String message) {
    super(message);
  }

  public ComponentException(String message, Throwable cause) {
    super(message, cause);
  }
}
