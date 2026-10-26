package org.openldes.ldesadmin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Starts the sandbox for real, in Docker, and checks that its LDES server and LDIO work together
 * out of the box: members sent to the LDIO end up in the LDES server, and an LDIO pipeline can
 * replicate the event stream again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "ldes-admin.monitor.initial-delay=PT1S",
    "ldes-admin.monitor.interval=PT2S"})
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class SandboxIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {
      };
  private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
      new ParameterizedTypeReference<>() {
      };
  private static final String MEMBER = """
      @prefix dcterms: <http://purl.org/dc/terms/> .
      @prefix prov:    <http://www.w3.org/ns/prov#> .
      @prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .

      <http://example.org/parcel/1/v1> a <https://data.vlaanderen.be/ns/perceel#Perceel> ;
          dcterms:isVersionOf <http://example.org/parcel/1> ;
          prov:generatedAtTime "2026-10-24T10:15:30Z"^^xsd:dateTime .
      """;

  @Autowired
  private RestTestClient client;

  @Test
  void startsALdesServerAndLdioThatWorkTogether() {
    client.get().uri("/api/v1/sandbox").exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.state").isEqualTo("STOPPED");

    Map<String, Object> sandbox = startSandbox();
    assertThat(sandbox).containsEntry("state", "RUNNING")
        .containsEntry("eventStreamUrl", "http://sandbox-ldes-server:8080/sandbox-parcels");
    String serverId = (String) object(sandbox, "ldesServer").get("id");
    String ldioId = (String) object(sandbox, "ldio").get("id");
    assertThat(object(sandbox, "ldesServer"))
        .containsEntry("internalUrl", "http://sandbox-ldes-server:8080");

    // Prepared: an event stream with two views, and a pipeline that feeds it
    client.get().uri("/api/v1/ldes-servers/{id}/eventstreams/sandbox-parcels", serverId)
        .exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.views").isEqualTo(List.of("by-page", "by-time"));
    // The simulation polls the event stream on the host name a pipeline of the LDIO uses
    client.post().uri("/api/v1/sandbox/poll")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("url", "http://sandbox-ldes-server:8080/sandbox-parcels"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.contentType").value(String.class,
            contentType -> assertThat(contentType).isNotBlank())
        .jsonPath("$.body").value(String.class,
            body -> assertThat(body).contains("sandbox-parcels"));
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        client.get().uri("/api/v1/ldios/{id}/pipelines/sandbox-ingest", ldioId).exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("RUNNING"));

    // Members sent to the LDIO end up in the event stream
    client.post().uri("/api/v1/ldios/{id}/pipelines/sandbox-ingest/members", ldioId)
        .contentType(MediaType.valueOf("text/turtle"))
        .body(MEMBER)
        .exchange()
        .expectStatus().isAccepted();
    await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
        client.get().uri("/api/v1/ldes-servers/{id}/statistics", serverId).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].collection").isEqualTo("sandbox-parcels")
            .jsonPath("$[0].ingestedMembers").isEqualTo(1)
            .jsonPath("$[0].backlog").isEqualTo(0)
            .jsonPath("$[0].views[?(@.view == 'by-page')].paginatedMembers").isEqualTo(
                List.of(1)));

    // ... where LDES-admin can browse them, although the server publishes internal IRIs
    Map<String, Object> root = client.get()
        .uri("/api/v1/ldes-servers/{id}/eventstreams/sandbox-parcels/views/by-page/fragment",
            serverId)
        .exchange()
        .expectStatus().isOk()
        .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    assertThat(root).containsEntry("url", "http://sandbox-ldes-server:8080/sandbox-parcels/by-page");
    String firstPage = (String) objects(root, "relations").getFirst().get("node");
    client.get()
        .uri(builder -> builder
            .path("/api/v1/ldes-servers/{id}/eventstreams/sandbox-parcels/views/by-page/fragment")
            .queryParam("url", firstPage).build(serverId))
        .exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.members[0].id").isEqualTo("http://example.org/parcel/1/v1");

    // ... and from which an LDIO replicates them again
    client.post().uri("/api/v1/ldios/{id}/pipelines", ldioId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("name", "replicate",
            "input", Map.of("name", "Ldio:LdesClient",
                "config", Map.of("urls", "http://sandbox-ldes-server:8080/sandbox-parcels/by-page")),
            "outputs", List.of(Map.of("name", "Ldio:ConsoleOut"))))
        .exchange()
        .expectStatus().isCreated();
    await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
      client.get().uri("/api/v1/ldios/{id}/ldes-clients", ldioId).exchange()
          .expectStatus().isOk()
          .expectBody().jsonPath("$.replicate").isEqualTo("SYNCHRONISING");
      client.get().uri("/api/v1/ldios/{id}/statistics", ldioId).exchange()
          .expectStatus().isOk()
          .expectBody().jsonPath("$[?(@.pipeline == 'replicate')].membersIn")
          .isEqualTo(List.of(1));
    });

    // LDES-admin noticed what happened
    List<Map<String, Object>> activity = client.get().uri("/api/v1/activity").exchange()
        .expectStatus().isOk()
        .expectBody(JSON_ARRAY).returnResult().getResponseBody();
    assertThat(activity).extracting(event -> event.get("title")).contains(
        "Local LDES server started", "Local LDIO started", "Event stream sandbox-parcels created",
        "View sandbox-parcels/by-page created", "Pipeline sandbox-ingest deployed",
        "Pipeline replicate deployed");
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        client.get().uri("/api/v1/dashboard/throughput").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$[0].label").isEqualTo("Sandbox LDES server"));

    // The sandbox runs a pipeline itself, and its HTTP output really ingests in the event stream
    // when executed, on the host name the pipeline uses
    Map<String, Object> run = client.post().uri("/api/v1/sandbox/runs")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
            "pipeline", Map.of("name", "parcels-from-sandbox",
                "input", Map.of("name", "Ldio:HttpIn", "adapter", Map.of("name", "Ldio:RdfAdapter")),
                "outputs", List.of(Map.of("name", "Ldio:HttpOut",
                    "config", Map.of("endpoint", "http://sandbox-ldes-server:8080/sandbox-parcels")))),
            "input", Map.of("content", MEMBER.replace("v1", "v2").replace("10:15", "11:15"),
                "contentType", "text/turtle"),
            "session", "sandbox-it",
            "executeOutputs", true))
        .exchange()
        .expectStatus().isOk()
        .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    assertThat(run).containsEntry("delivered", true);
    assertThat(objects(run, "steps").getLast())
        .containsEntry("status", "ok")
        .hasEntrySatisfying("summary", summary -> assertThat((String) summary)
            .matches("POST http://localhost:\\d+/sandbox-parcels: 201"));
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        client.get().uri("/api/v1/ldes-servers/{id}/statistics", serverId).exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$[0].ingestedMembers").isEqualTo(2));

    // Starting again changes nothing
    assertThat(object(startSandbox(), "ldesServer")).containsEntry("id", serverId);
    client.get().uri("/api/v1/ldes-servers").exchange()
        .expectBody(JSON_ARRAY).value(servers -> assertThat(servers).hasSize(1));

    client.delete().uri("/api/v1/sandbox").exchange()
        .expectStatus().isNoContent();
    client.get().uri("/api/v1/sandbox").exchange()
        .expectBody().jsonPath("$.state").isEqualTo("STOPPED");
    client.get().uri("/api/v1/ldios").exchange()
        .expectBody(JSON_ARRAY).value(ldios -> assertThat(ldios).isEmpty());
  }

  private Map<String, Object> startSandbox() {
    return client.post().uri("/api/v1/sandbox").exchange()
        .expectStatus().isOk()
        .expectBody(JSON_OBJECT).returnResult().getResponseBody();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Map<String, Object> json, String field) {
    return (Map<String, Object>) json.get(field);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> objects(Map<String, Object> json, String field) {
    return (List<Map<String, Object>>) json.get(field);
  }
}
