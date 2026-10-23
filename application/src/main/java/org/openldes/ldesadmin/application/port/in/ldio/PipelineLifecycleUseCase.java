package org.openldes.ldesadmin.application.port.in.ldio;

import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;

/**
 * Pauses and resumes pipelines.
 *
 * @see PipelineUseCase for the exceptions thrown
 */
public interface PipelineLifecycleUseCase {

  PipelineStatus status(InstanceId ldioId, String pipeline);

  /**
   * @throws IllegalPipelineStateException if the pipeline is not running
   */
  PipelineStatus halt(InstanceId ldioId, String pipeline);

  /**
   * @throws IllegalPipelineStateException if the pipeline is not halted
   */
  PipelineStatus resume(InstanceId ldioId, String pipeline);
}
