package org.openldes.ldesadmin.adapter.out.ldio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.AdapterFixtures;
import org.openldes.ldesadmin.adapter.out.http.InstanceRestClients;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldio.ComponentCatalog;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.LdesClientStatus;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.openldes.ldesadmin.model.ldio.StatusChangeSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestLdioClientTest {

  private static final String PIPELINES_JSON = """
      [{
        "name": "parcels",
        "description": "Parcels to LDES",
        "status": "RUNNING",
        "updateSource": "AUTO",
        "input": {
          "name": "Ldio:HttpIn",
          "adapter": {"name": "Ldio:RdfAdapter", "config": {}}
        },
        "transformers": [{
          "name": "Ldio:VersionObjectCreator",
          "config": {"member-type": "https://data.vlaanderen.be/ns/perceel#Perceel"}
        }],
        "outputs": [{
          "name": "Ldio:HttpOut",
          "config": {"endpoint": "http://ldes:8080/parcels", "rdf-writer.content-type": "text/turtle"}
        }]
      }]
      """;

  private final Instance ldio = AdapterFixtures.ldio();
  private MockRestServiceServer mockServer;
  private RestLdioClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    client = new RestLdioClient(new InstanceRestClients(builder), Clock.systemUTC(),
        Duration.ofSeconds(2));
  }

  @Test
  void listsTheDeployedPipelines() {
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline"))
        .andRespond(withSuccess(PIPELINES_JSON, MediaType.APPLICATION_JSON));

    List<DeployedPipeline> pipelines = client.getPipelines(ldio);

    assertThat(pipelines).singleElement().satisfies(deployed -> {
      assertThat(deployed.status()).isEqualTo(PipelineStatus.RUNNING);
      assertThat(deployed.updateSource()).isEqualTo(StatusChangeSource.AUTO);
      Pipeline pipeline = deployed.pipeline();
      assertThat(pipeline.input().optionalAdapter()).map(ComponentDefinition::name)
          .contains("Ldio:RdfAdapter");
      assertThat(pipeline.transformers()).extracting(ComponentDefinition::name)
          .containsExactly("Ldio:VersionObjectCreator");
      assertThat(pipeline.outputs().getFirst().config())
          .containsEntry("endpoint", "http://ldes:8080/parcels")
          .containsEntry("rdf-writer.content-type", "text/turtle");
    });
  }

  @Test
  void createsAPipeline() {
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.name").value("parcels"))
        .andExpect(jsonPath("$.input.adapter.name").value("Ldio:RdfAdapter"))
        .andExpect(jsonPath("$.outputs[0].config['rdf-writer']['content-type']")
            .value("text/turtle"))
        .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(PIPELINES_JSON.strip().substring(1, PIPELINES_JSON.strip().length() - 1)));

    Pipeline pipeline = new Pipeline("parcels", "Parcels to LDES",
        new InputDefinition("Ldio:HttpIn", null, ComponentDefinition.of("Ldio:RdfAdapter")),
        List.of(),
        List.of(new ComponentDefinition("Ldio:HttpOut", Map.of(
            "endpoint", "http://ldes:8080/parcels",
            "rdf-writer", Map.of("content-type", "text/turtle")))));

    assertThat(client.createPipeline(ldio, pipeline).name()).isEqualTo("parcels");
  }

  @Test
  void waitsUntilAnAcceptedDeletionCompleted() {
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline/parcels"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.ACCEPTED));
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline"))
        .andRespond(withSuccess(PIPELINES_JSON.replace("RUNNING", "STOPPING"),
            MediaType.APPLICATION_JSON));
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    client.deletePipeline(ldio, "parcels");

    mockServer.verify();
  }

  @Test
  void givesUpWaitingForADeletionAfterTheTimeout() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer impatientServer = MockRestServiceServer.bindTo(builder).build();
    RestLdioClient impatientClient = new RestLdioClient(new InstanceRestClients(builder),
        Clock.systemUTC(), Duration.ofMillis(300));
    impatientServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline/parcels"))
        .andRespond(withStatus(HttpStatus.ACCEPTED));
    impatientServer.expect(manyTimes(), requestTo("http://ldio.example.org/admin/api/v1/pipeline"))
        .andRespond(withSuccess(PIPELINES_JSON, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> impatientClient.deletePipeline(ldio, "parcels"))
        .isInstanceOf(RemoteInstanceException.class)
        .hasMessageContaining("did not remove pipeline parcels");
  }

  @Test
  void parsesTheStatusReturnedWhenHaltingAPipeline() {
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline/parcels/halt"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("\"HALTED\"", MediaType.APPLICATION_JSON));

    assertThat(client.halt(ldio, "parcels")).isEqualTo(PipelineStatus.HALTED);
  }

  @Test
  void readsTheComponentCatalog() {
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/catalog"))
        .andRespond(withSuccess("""
            {"inputs": ["Ldio:LdesClient", "Ldio:HttpIn"], "adapters": ["Ldio:RdfAdapter"],
             "transformers": [], "outputs": ["Ldio:HttpOut", "Ldio:ConsoleOut"]}
            """, MediaType.APPLICATION_JSON));

    ComponentCatalog catalog = client.getCatalog(ldio);

    assertThat(catalog.inputs()).containsExactly("Ldio:HttpIn", "Ldio:LdesClient");
    assertThat(catalog.outputs()).containsExactly("Ldio:ConsoleOut", "Ldio:HttpOut");
  }

  @Test
  void readsThroughputPerPipelineFromTheMetrics() {
    mockServer.expect(requestTo("http://ldio.example.org/actuator/prometheus"))
        .andRespond(withSuccess("""
            # TYPE ldio_data_in_total counter
            ldio_data_in_total{ldio_type="Ldio:HttpIn",pipeline_name="parcels"} 120.0
            ldio_data_in_total{ldio_type="Ldio:LdesClient",pipeline_name="addresses"} 40.0
            # TYPE ldio_data_out_total counter
            ldio_data_out_total{pipeline_name="parcels"} 118.0
            """, MediaType.TEXT_PLAIN));

    assertThat(client.getStatistics(ldio)).containsExactly(
        new PipelineStatistics("addresses", 40, 0),
        new PipelineStatistics("parcels", 120, 118));
  }

  @Test
  void readsTheProgressOfTheLdesClients() {
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline/ldes-client"))
        .andRespond(withSuccess("""
            [{"pipeline": "replicate", "status": "SYNCHRONISING"},
             {"pipeline": "backfill", "status": "REPLICATING"},
             {"pipeline": "future", "status": "SOMETHING_NEW"}]
            """, MediaType.APPLICATION_JSON));

    assertThat(client.getLdesClientStatuses(ldio)).containsExactly(
        Map.entry("backfill", LdesClientStatus.REPLICATING),
        Map.entry("replicate", LdesClientStatus.SYNCHRONISING));
  }

  @Test
  void reportsNoLdesClientsForAnLdioThatDoesNotTrackThem() {
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline/ldes-client"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(client.getLdesClientStatuses(ldio)).isEmpty();
  }

  @Test
  void postsAMemberToTheHttpInputOfAPipeline() {
    mockServer.expect(requestTo("http://ldio.example.org/parcels"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType("text/turtle"))
        .andExpect(content().string("<a> <b> <c> ."))
        .andRespond(withStatus(HttpStatus.ACCEPTED));

    client.send(ldio, "parcels", "<a> <b> <c> .", "text/turtle");

    mockServer.verify();
  }
}
