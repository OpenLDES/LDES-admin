package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import org.openldes.ldesadmin.model.sandbox.LivePipelineRun;

record LiveRunWebModel(boolean active, long received, PipelineRunWebModel latest, String error) {
  static LiveRunWebModel fromDomain(LivePipelineRun run) {
    return new LiveRunWebModel(
        run.active(),
        run.received(),
        run.latest() == null ? null : PipelineRunWebModel.fromDomain(run.latest()),
        run.error());
  }
}
