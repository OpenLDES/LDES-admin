package org.openldes.ldesadmin.application.port.out;

/** An LDES server or LDIO could not fulfil a request made by an outbound adapter. */
public class RemoteInstanceException extends RuntimeException {

  public enum Reason {
    /** The requested resource (event stream, view, pipeline, ...) does not exist. */
    NOT_FOUND,
    /** The instance refused the request, e.g. because the RDF or pipeline is invalid. */
    REJECTED,
    /** The instance could not be reached or failed to process the request. */
    UNAVAILABLE
  }

  private final Reason reason;

  public RemoteInstanceException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public RemoteInstanceException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
