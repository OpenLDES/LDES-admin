package org.openldes.ldesadmin.application.port.in.ldio;

import java.util.List;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.Pipeline;

/**
 * Manages the pipelines of an LDIO.
 *
 * <p>All methods throw an
 * {@link org.openldes.ldesadmin.application.port.in.instance.InstanceNotFoundException} if the
 * LDIO is unknown, and a {@link org.openldes.ldesadmin.application.port.out.RemoteInstanceException}
 * if the LDIO cannot fulfil the request.
 */
public interface PipelineUseCase {

  List<DeployedPipeline> list(InstanceId ldioId);

  /**
   * @throws PipelineNotFoundException if no pipeline with that name is deployed
   */
  DeployedPipeline get(InstanceId ldioId, String pipeline);

  DeployedPipeline create(InstanceId ldioId, Pipeline pipeline);

  /**
   * Replaces a deployed pipeline with a new definition. LDIO has no update operation, so the
   * pipeline is deleted and recreated.
   *
   * @throws PipelineNotFoundException if no pipeline with that name is deployed
   */
  DeployedPipeline update(InstanceId ldioId, Pipeline pipeline);

  void delete(InstanceId ldioId, String pipeline);
}
