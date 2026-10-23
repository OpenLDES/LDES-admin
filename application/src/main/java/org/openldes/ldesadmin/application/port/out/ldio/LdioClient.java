package org.openldes.ldesadmin.application.port.out.ldio;

import java.util.List;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldio.ComponentCatalog;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;

/**
 * Talks to the pipeline management API and metrics of an LDIO.
 *
 * <p>Implementations throw a {@link org.openldes.ldesadmin.application.port.out.RemoteInstanceException}
 * when the LDIO cannot fulfil a request.
 */
public interface LdioClient {

  List<DeployedPipeline> getPipelines(Instance ldio);

  DeployedPipeline createPipeline(Instance ldio, Pipeline pipeline);

  /**
   * Deletes a pipeline and returns once it is gone.
   */
  void deletePipeline(Instance ldio, String pipeline);

  PipelineStatus getStatus(Instance ldio, String pipeline);

  PipelineStatus halt(Instance ldio, String pipeline);

  PipelineStatus resume(Instance ldio, String pipeline);

  ComponentCatalog getCatalog(Instance ldio);

  List<PipelineStatistics> getStatistics(Instance ldio);
}
