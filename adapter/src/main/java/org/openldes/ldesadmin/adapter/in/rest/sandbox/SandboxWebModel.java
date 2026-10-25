package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import java.net.URI;
import org.openldes.ldesadmin.adapter.in.rest.instance.InstanceWebModel;
import org.openldes.ldesadmin.model.sandbox.Sandbox;

/**
 * @param ldesServer          the sandbox LDES server, {@code null} when it does not run
 * @param ldio                the sandbox LDIO, {@code null} when it does not run
 * @param eventStreamUrl      where LDIO pipelines send members to, and read them from
 */
public record SandboxWebModel(
    Sandbox.State state,
    InstanceWebModel ldesServer,
    InstanceWebModel ldio,
    String eventStream,
    String ingestPipeline,
    URI eventStreamUrl) {

  public static SandboxWebModel fromDomain(Sandbox sandbox) {
    return new SandboxWebModel(sandbox.state(),
        sandbox.ldesServer() == null ? null : InstanceWebModel.fromDomain(sandbox.ldesServer()),
        sandbox.ldio() == null ? null : InstanceWebModel.fromDomain(sandbox.ldio()),
        Sandbox.EVENT_STREAM, Sandbox.INGEST_PIPELINE,
        sandbox.eventStreamUrlForLdio().orElse(null));
  }
}
