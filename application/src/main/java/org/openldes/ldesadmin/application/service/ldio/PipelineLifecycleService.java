package org.openldes.ldesadmin.application.service.ldio;

import org.openldes.ldesadmin.application.port.in.ldio.IllegalPipelineStateException;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineLifecycleUseCase;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;

@ApplicationService
public class PipelineLifecycleService implements PipelineLifecycleUseCase {

  private final InstanceLookup instanceLookup;
  private final LdioClient ldioClient;

  public PipelineLifecycleService(InstanceLookup instanceLookup, LdioClient ldioClient) {
    this.instanceLookup = instanceLookup;
    this.ldioClient = ldioClient;
  }

  @Override
  public PipelineStatus status(InstanceId ldioId, String pipeline) {
    return ldioClient.getStatus(instanceLookup.requireLdio(ldioId), pipeline);
  }

  @Override
  public PipelineStatus halt(InstanceId ldioId, String pipeline) {
    Instance ldio = instanceLookup.requireLdio(ldioId);
    PipelineStatus current = ldioClient.getStatus(ldio, pipeline);
    if (!current.canBeHalted()) {
      throw new IllegalPipelineStateException(pipeline, current, "halt");
    }
    return ldioClient.halt(ldio, pipeline);
  }

  @Override
  public PipelineStatus resume(InstanceId ldioId, String pipeline) {
    Instance ldio = instanceLookup.requireLdio(ldioId);
    PipelineStatus current = ldioClient.getStatus(ldio, pipeline);
    if (!current.canBeResumed()) {
      throw new IllegalPipelineStateException(pipeline, current, "resume");
    }
    return ldioClient.resume(ldio, pipeline);
  }
}
