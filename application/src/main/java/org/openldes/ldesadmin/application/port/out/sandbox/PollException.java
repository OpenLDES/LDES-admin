package org.openldes.ldesadmin.application.port.out.sandbox;

/**
 * A URL could not be polled: it is unreachable, answered with an error, or answered what LDIO would
 * not pass on to a pipeline.
 */
public class PollException extends RuntimeException {

  public PollException(String message) {
    super(message);
  }

  public PollException(String message, Throwable cause) {
    super(message, cause);
  }
}
