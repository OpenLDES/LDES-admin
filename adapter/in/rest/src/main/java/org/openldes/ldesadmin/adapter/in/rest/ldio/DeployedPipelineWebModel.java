package org.openldes.ldesadmin.adapter.in.rest.ldio;

import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.openldes.ldesadmin.model.ldio.StatusChangeSource;

public record DeployedPipelineWebModel(
    PipelineWebModel pipeline, PipelineStatus status, StatusChangeSource updateSource) {

  public static DeployedPipelineWebModel fromDomain(DeployedPipeline deployed) {
    return new DeployedPipelineWebModel(
        PipelineWebModel.fromDomain(deployed.pipeline()),
        deployed.status(),
        deployed.updateSource());
  }
}
