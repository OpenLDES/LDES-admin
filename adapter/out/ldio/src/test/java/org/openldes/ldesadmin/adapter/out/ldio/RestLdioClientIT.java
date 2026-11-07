package org.openldes.ldesadmin.adapter.out.ldio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.out.health.ActuatorHealthProbe;
import org.openldes.ldesadmin.adapter.out.http.InstanceRestClients;
import org.openldes.ldesadmin.adapter.out.runtime.DockerLocalInstanceRuntime;
import org.openldes.ldesadmin.adapter.out.runtime.LocalRuntimeProperties;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime.LocalRuntimeHandle;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldio.ComponentCatalog;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.springframework.web.client.RestClient;

/** Runs the LDIO client against a real LDIO, started by the local runtime adapter. */
class RestLdioClientIT {

  private static DockerLocalInstanceRuntime runtime;
  private static Instance ldio;
  private static RestLdioClient client;

  @BeforeAll
  static void startLdio() {
    runtime =
        new DockerLocalInstanceRuntime(
            new LocalRuntimeProperties(
                "openldes/ldes-server:4.2.1",
                "openldes/ldi-orchestrator:3.1.1",
                "postgres:16-alpine",
                Duration.ofMinutes(5),
                "*/5 * * * * *"));
    LocalRuntimeHandle handle = runtime.start(InstanceType.LDIO, "it-ldio");
    ldio =
        Instance.local(
            InstanceType.LDIO,
            "it-ldio",
            handle.url(),
            handle.internalUrl(),
            handle.runtimeReference(),
            true,
            Instant.now());
    client =
        new RestLdioClient(
            new InstanceRestClients(RestClient.builder()),
            Clock.systemUTC(),
            Duration.ofSeconds(30));
  }

  @AfterAll
  static void stopLdio() {
    if (ldio != null) {
      runtime.stop(ldio.runtimeReference());
    }
  }

  @Test
  void isHealthy() {
    ActuatorHealthProbe probe =
        new ActuatorHealthProbe(new InstanceRestClients(RestClient.builder()), Clock.systemUTC());

    assertThat(probe.check(ldio).isUp()).isTrue();
  }

  @Test
  void managesTheLifecycleOfAPipeline() {
    DeployedPipeline created = client.createPipeline(ldio, httpToConsole("it-lifecycle"));
    assertThat(created.name()).isEqualTo("it-lifecycle");
    awaitRunning("it-lifecycle");

    assertThat(client.getPipelines(ldio))
        .extracting(DeployedPipeline::name)
        .contains("it-lifecycle");
    assertThat(client.halt(ldio, "it-lifecycle")).isEqualTo(PipelineStatus.HALTED);
    assertThat(client.resume(ldio, "it-lifecycle")).isEqualTo(PipelineStatus.RUNNING);

    client.deletePipeline(ldio, "it-lifecycle");

    assertThat(client.getPipelines(ldio))
        .extracting(DeployedPipeline::name)
        .doesNotContain("it-lifecycle");
  }

  @Test
  void listsTheInstalledComponents() {
    ComponentCatalog catalog = client.getCatalog(ldio);

    assertThat(catalog.inputs()).contains("Ldio:HttpIn", "Ldio:LdesClient");
    assertThat(catalog.adapters()).contains("Ldio:RdfAdapter");
    assertThat(catalog.transformers()).contains("Ldio:VersionObjectCreator");
    assertThat(catalog.outputs()).contains("Ldio:HttpOut", "Ldio:ConsoleOut");
  }

  @Test
  void countsTheMembersFlowingThroughAPipeline() {
    client.createPipeline(ldio, httpToConsole("it-metrics"));
    awaitRunning("it-metrics");

    client.send(
        ldio,
        "it-metrics",
        "<http://example.org/parcel/1> a <http://example.org/Parcel> .",
        "text/turtle");

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(client.getStatistics(ldio))
                    .contains(new PipelineStatistics("it-metrics", 1, 1)));
  }

  @Test
  void reportsNoLdesClientsWithoutPipelinesThatReadAnLdes() {
    assertThat(client.getLdesClientStatuses(ldio)).isEmpty();
  }

  private static Pipeline httpToConsole(String name) {
    return new Pipeline(
        name,
        "Integration test",
        new InputDefinition("Ldio:HttpIn", null, ComponentDefinition.of("Ldio:RdfAdapter")),
        List.of(),
        List.of(ComponentDefinition.of("Ldio:ConsoleOut")));
  }

  private static void awaitRunning(String pipeline) {
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> client.getStatus(ldio, pipeline) == PipelineStatus.RUNNING);
  }
}
