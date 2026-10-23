package org.openldes.ldesadmin.application.service.ldio;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineNotFoundException;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineUseCase;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.Pipeline;

@ApplicationService
public class PipelineService implements PipelineUseCase {

  private final InstanceLookup instanceLookup;
  private final LdioClient ldioClient;

  public PipelineService(InstanceLookup instanceLookup, LdioClient ldioClient) {
    this.instanceLookup = instanceLookup;
    this.ldioClient = ldioClient;
  }

  @Override
  public List<DeployedPipeline> list(InstanceId ldioId) {
    return ldioClient.getPipelines(instanceLookup.requireLdio(ldioId));
  }

  @Override
  public DeployedPipeline get(InstanceId ldioId, String pipeline) {
    return find(instanceLookup.requireLdio(ldioId), pipeline);
  }

  @Override
  public DeployedPipeline create(InstanceId ldioId, Pipeline pipeline) {
    return ldioClient.createPipeline(instanceLookup.requireLdio(ldioId), pipeline);
  }

  @Override
  public DeployedPipeline update(InstanceId ldioId, Pipeline pipeline) {
    Instance ldio = instanceLookup.requireLdio(ldioId);
    find(ldio, pipeline.name());
    ldioClient.deletePipeline(ldio, pipeline.name());
    return ldioClient.createPipeline(ldio, pipeline);
  }

  @Override
  public void delete(InstanceId ldioId, String pipeline) {
    Instance ldio = instanceLookup.requireLdio(ldioId);
    find(ldio, pipeline);
    ldioClient.deletePipeline(ldio, pipeline);
  }

  private DeployedPipeline find(Instance ldio, String pipeline) {
    return ldioClient.getPipelines(ldio).stream()
        .filter(deployed -> deployed.name().equals(pipeline))
        .findFirst()
        .orElseThrow(() -> new PipelineNotFoundException(pipeline));
  }
}
