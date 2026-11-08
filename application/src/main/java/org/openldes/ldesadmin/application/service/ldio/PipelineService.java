package org.openldes.ldesadmin.application.service.ldio;

import java.util.List;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineNotFoundException;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineUseCase;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.application.service.monitoring.ActivityRecorder;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;

@ApplicationService
public class PipelineService implements PipelineUseCase {

  private final InstanceLookup instanceLookup;
  private final LdioClient ldioClient;
  private final ActivityRecorder activity;

  public PipelineService(
      InstanceLookup instanceLookup, LdioClient ldioClient, ActivityRecorder activity) {
    this.instanceLookup = instanceLookup;
    this.ldioClient = ldioClient;
    this.activity = activity;
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
    Instance ldio = instanceLookup.requireLdio(ldioId);
    DeployedPipeline created = ldioClient.createPipeline(ldio, pipeline);
    activity.success(ldio, "Pipeline " + pipeline.name() + " deployed");
    return created;
  }

  @Override
  public DeployedPipeline update(InstanceId ldioId, Pipeline pipeline) {
    Instance ldio = instanceLookup.requireLdio(ldioId);
    find(ldio, pipeline.name());
    ldioClient.deletePipeline(ldio, pipeline.name());
    DeployedPipeline updated = ldioClient.createPipeline(ldio, pipeline);
    activity.info(ldio, "Pipeline " + pipeline.name() + " updated");
    return updated;
  }

  @Override
  public void delete(InstanceId ldioId, String pipeline) {
    Instance ldio = instanceLookup.requireLdio(ldioId);
    find(ldio, pipeline);
    ldioClient.deletePipeline(ldio, pipeline);
    activity.record(ldio, Severity.WARN, "Pipeline " + pipeline + " deleted", null);
  }

  private DeployedPipeline find(Instance ldio, String pipeline) {
    return ldioClient.getPipelines(ldio).stream()
        .filter(deployed -> deployed.name().equals(pipeline))
        .findFirst()
        .orElseThrow(() -> new PipelineNotFoundException(pipeline));
  }
}
