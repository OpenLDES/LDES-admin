package org.openldes.ldesadmin.adapter.out.ldio;

import static org.openldes.ldesadmin.adapter.out.http.RemoteCalls.call;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.openldes.ldesadmin.adapter.out.http.InstanceRestClients;
import org.openldes.ldesadmin.adapter.out.http.PrometheusMetrics;
import org.openldes.ldesadmin.adapter.out.ldio.LdioApi.CatalogTO;
import org.openldes.ldesadmin.adapter.out.ldio.LdioApi.PipelineTO;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException.Reason;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldio.ComponentCatalog;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the pipeline management API ({@code /admin/api/v1}) and the Prometheus metrics of an
 * LDIO.
 */
@Component
public class RestLdioClient implements LdioClient {

  static final String DATA_IN = "ldio_data_in_total";
  static final String DATA_OUT = "ldio_data_out_total";

  private static final String PIPELINES = "/admin/api/v1/pipeline";
  private static final String PIPELINE = PIPELINES + "/{pipeline}";
  private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

  private final InstanceRestClients restClients;
  private final Clock clock;
  private final Duration deleteTimeout;

  public RestLdioClient(InstanceRestClients restClients, Clock clock,
      @Value("${ldes-admin.ldio.delete-timeout:PT30S}") Duration deleteTimeout) {
    this.restClients = restClients;
    this.clock = clock;
    this.deleteTimeout = deleteTimeout;
  }

  @Override
  public List<DeployedPipeline> getPipelines(Instance ldio) {
    List<PipelineTO> pipelines = call(ldio, "list pipelines", () -> rest(ldio).get()
        .uri(PIPELINES).accept(MediaType.APPLICATION_JSON)
        .retrieve().body(new ParameterizedTypeReference<List<PipelineTO>>() {
        }));
    return pipelines == null ? List.of()
        : pipelines.stream().map(LdioPipelineMapper::toModel).toList();
  }

  @Override
  public DeployedPipeline createPipeline(Instance ldio, Pipeline pipeline) {
    PipelineTO created = call(ldio, "create pipeline " + pipeline.name(), () -> rest(ldio).post()
        .uri(PIPELINES).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
        .body(LdioPipelineMapper.toConfig(pipeline))
        .retrieve().body(PipelineTO.class));
    if (created == null) {
      throw new RemoteInstanceException(Reason.UNAVAILABLE,
          "LDIO '%s' did not return the created pipeline".formatted(ldio.name()));
    }
    return LdioPipelineMapper.toModel(created);
  }

  @Override
  public void deletePipeline(Instance ldio, String pipeline) {
    ResponseEntity<Void> response = call(ldio, "delete pipeline " + pipeline, () -> rest(ldio)
        .delete().uri(PIPELINE, pipeline)
        .retrieve().toBodilessEntity());
    if (response.getStatusCode() == HttpStatus.ACCEPTED) {
      awaitRemoval(ldio, pipeline);
    }
  }

  @Override
  public PipelineStatus getStatus(Instance ldio, String pipeline) {
    return status(ldio, "get status of " + pipeline, rest(ldio).get()
        .uri(PIPELINE + "/status", pipeline));
  }

  @Override
  public PipelineStatus halt(Instance ldio, String pipeline) {
    return status(ldio, "halt pipeline " + pipeline, rest(ldio).post()
        .uri(PIPELINE + "/halt", pipeline));
  }

  @Override
  public PipelineStatus resume(Instance ldio, String pipeline) {
    return status(ldio, "resume pipeline " + pipeline, rest(ldio).post()
        .uri(PIPELINE + "/resume", pipeline));
  }

  @Override
  public ComponentCatalog getCatalog(Instance ldio) {
    CatalogTO catalog = call(ldio, "get component catalog", () -> rest(ldio).get()
        .uri("/admin/api/v1/catalog").accept(MediaType.APPLICATION_JSON)
        .retrieve().body(CatalogTO.class));
    return catalog == null ? new ComponentCatalog(null, null, null, null)
        : new ComponentCatalog(catalog.inputs(), catalog.adapters(), catalog.transformers(),
            catalog.outputs());
  }

  @Override
  public List<PipelineStatistics> getStatistics(Instance ldio) {
    String exposition = call(ldio, "get metrics", () -> rest(ldio).get()
        .uri("/actuator/prometheus").accept(MediaType.TEXT_PLAIN)
        .retrieve().body(String.class));
    PrometheusMetrics metrics = PrometheusMetrics.parse(exposition == null ? "" : exposition);
    Map<String, Long> in = countPerPipeline(metrics, DATA_IN);
    Map<String, Long> out = countPerPipeline(metrics, DATA_OUT);
    TreeSet<String> pipelines = new TreeSet<>(in.keySet());
    pipelines.addAll(out.keySet());
    return pipelines.stream()
        .map(pipeline -> new PipelineStatistics(pipeline, in.getOrDefault(pipeline, 0L),
            out.getOrDefault(pipeline, 0L)))
        .toList();
  }

  private RestClient rest(Instance ldio) {
    return restClients.forInstance(ldio);
  }

  private static PipelineStatus status(Instance ldio, String action,
      RestClient.RequestHeadersSpec<?> request) {
    String status = call(ldio, action, () -> request.accept(MediaType.TEXT_PLAIN,
        MediaType.APPLICATION_JSON).retrieve().body(String.class));
    try {
      return LdioPipelineMapper.parseStatus(status);
    } catch (IllegalArgumentException e) {
      throw new RemoteInstanceException(Reason.UNAVAILABLE,
          "LDIO '%s' returned an unknown pipeline status: %s".formatted(ldio.name(), status), e);
    }
  }

  private static Map<String, Long> countPerPipeline(PrometheusMetrics metrics, String name) {
    Map<String, Long> counts = new TreeMap<>();
    metrics.samples(name).forEach(sample -> counts.merge(sample.label("pipeline_name"),
        sample.longValue(), Long::sum));
    return counts;
  }

  /**
   * LDIO stops a pipeline gracefully before deleting it.
   */
  private void awaitRemoval(Instance ldio, String pipeline) {
    Instant deadline = clock.instant().plus(deleteTimeout);
    while (getPipelines(ldio).stream().anyMatch(deployed -> deployed.name().equals(pipeline))) {
      if (clock.instant().isAfter(deadline)) {
        throw new RemoteInstanceException(Reason.UNAVAILABLE,
            "LDIO '%s' did not remove pipeline %s within %s".formatted(ldio.name(), pipeline,
                deleteTimeout));
      }
      try {
        Thread.sleep(POLL_INTERVAL);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RemoteInstanceException(Reason.UNAVAILABLE,
            "Interrupted while waiting for pipeline %s to be removed".formatted(pipeline), e);
      }
    }
  }
}
