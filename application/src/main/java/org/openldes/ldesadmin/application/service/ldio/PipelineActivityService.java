package org.openldes.ldesadmin.application.service.ldio;

import java.util.Map;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineActivityUseCase;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineNotFoundException;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.LdesClientStatus;

@ApplicationService
public class PipelineActivityService implements PipelineActivityUseCase {

  static final String HTTP_INPUT = "Ldio:HttpIn";

  private final InstanceLookup instanceLookup;
  private final LdioClient ldioClient;

  public PipelineActivityService(InstanceLookup instanceLookup, LdioClient ldioClient) {
    this.instanceLookup = instanceLookup;
    this.ldioClient = ldioClient;
  }

  @Override
  public Map<String, LdesClientStatus> ldesClientStatuses(InstanceId ldioId) {
    return ldioClient.getLdesClientStatuses(instanceLookup.requireLdio(ldioId));
  }

  @Override
  public void send(InstanceId ldioId, String pipeline, String content, String contentType) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("A message must not be empty");
    }
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("A message needs a content type");
    }
    Instance ldio = instanceLookup.requireLdio(ldioId);
    DeployedPipeline deployed = ldioClient.getPipelines(ldio).stream()
        .filter(candidate -> candidate.name().equals(pipeline))
        .findFirst()
        .orElseThrow(() -> new PipelineNotFoundException(pipeline));
    String input = deployed.pipeline().input().name();
    if (!HTTP_INPUT.equals(input)) {
      throw new IllegalArgumentException(
          "Pipeline %s reads from %s, so it does not accept members over HTTP"
              .formatted(pipeline, input));
    }
    ldioClient.send(ldio, pipeline, content, contentType);
  }
}
