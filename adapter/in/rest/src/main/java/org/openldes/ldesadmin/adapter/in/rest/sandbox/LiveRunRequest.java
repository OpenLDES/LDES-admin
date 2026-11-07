package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.openldes.ldesadmin.adapter.in.rest.ldio.PipelineWebModel;
import org.openldes.ldesadmin.model.sandbox.RunOptions;

record LiveRunRequest(
    @NotNull @Valid PipelineWebModel pipeline, @NotBlank String session, Boolean executeOutputs) {
  RunOptions toOptions() {
    return new RunOptions(session, Boolean.TRUE.equals(executeOutputs));
  }
}
