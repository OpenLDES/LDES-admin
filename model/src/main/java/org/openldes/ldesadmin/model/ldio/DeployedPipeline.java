package org.openldes.ldesadmin.model.ldio;

import java.util.Objects;

/**
 * A pipeline as it is deployed on an LDIO.
 */
public record DeployedPipeline(Pipeline pipeline, PipelineStatus status,
                               StatusChangeSource updateSource) {

  public DeployedPipeline {
    Objects.requireNonNull(pipeline, "'pipeline' must not be null");
    Objects.requireNonNull(status, "'status' must not be null");
  }

  public String name() {
    return pipeline.name();
  }
}
