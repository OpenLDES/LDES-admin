package org.openldes.ldesadmin.model.sandbox;

import java.net.URI;
import java.util.Optional;
import org.openldes.ldesadmin.model.instance.Instance;

/**
 * A local LDES server and LDIO to experiment with. They run in the same Docker network, so the LDIO
 * reaches the LDES server on its internal URL: out of the box, the {@value #INGEST_PIPELINE}
 * pipeline of the LDIO feeds the {@value #EVENT_STREAM} event stream.
 *
 * @param ldesServer the sandbox LDES server, {@code null} when it does not run
 * @param ldio the sandbox LDIO, {@code null} when it does not run
 */
public record Sandbox(Instance ldesServer, Instance ldio) {

  public static final String LDES_SERVER_NAME = "Sandbox LDES server";
  public static final String LDIO_NAME = "Sandbox LDIO";
  public static final String EVENT_STREAM = "sandbox-parcels";
  public static final String INGEST_PIPELINE = "sandbox-ingest";

  public enum State {
    STOPPED,
    /** Only one of both runs */
    PARTIAL,
    RUNNING
  }

  public static Sandbox stopped() {
    return new Sandbox(null, null);
  }

  public State state() {
    if (ldesServer != null && ldio != null) {
      return State.RUNNING;
    }
    return ldesServer == null && ldio == null ? State.STOPPED : State.PARTIAL;
  }

  /**
   * Where the {@value #INGEST_PIPELINE} pipeline sends members: the event stream, on the URL the
   * LDIO reaches the LDES server on.
   */
  public Optional<URI> eventStreamUrlForLdio() {
    return Optional.ofNullable(ldesServer)
        .map(server -> server.internalUrl() == null ? server.url() : server.internalUrl())
        .map(url -> URI.create(url + "/" + EVENT_STREAM));
  }
}
