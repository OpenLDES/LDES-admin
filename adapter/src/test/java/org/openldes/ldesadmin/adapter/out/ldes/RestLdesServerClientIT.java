package org.openldes.ldesadmin.adapter.out.ldes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.openldes.ldesadmin.model.ldes.BackgroundProcess;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.Fragment;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldes.View;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.springframework.web.client.RestClient;

/**
 * Runs the LDES server client against a real LDES server (with Postgres), started by the local
 * runtime adapter.
 */
class RestLdesServerClientIT {

  private static final String EVENT_STREAM = """
      @prefix ldes:    <https://w3id.org/ldes#> .
      @prefix dcterms: <http://purl.org/dc/terms/> .
      @prefix tree:    <https://w3id.org/tree#> .
      @prefix sh:      <http://www.w3.org/ns/shacl#> .
      @prefix server:  <http://localhost:8080/> .

      server:it-parcels a ldes:EventStream ;
          ldes:timestampPath dcterms:created ;
          ldes:versionOfPath dcterms:isVersionOf ;
          tree:shape server:it-parcels-shape .

      server:it-parcels-shape a sh:NodeShape .
      """;
  private static final String VIEW = """
      @prefix tree:   <https://w3id.org/tree#> .
      @prefix server: <http://localhost:8080/it-parcels/> .

      server:by-page tree:viewDescription [
          tree:fragmentationStrategy () ;
          tree:pageSize "50"^^<http://www.w3.org/2001/XMLSchema#int>
      ] .
      """;
  private static final String MEMBER = """
      @prefix dcterms: <http://purl.org/dc/terms/> .
      @prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .

      <http://example.org/parcel/1/2026-10-23> a <http://example.org/Parcel> ;
          dcterms:isVersionOf <http://example.org/parcel/1> ;
          dcterms:created "2026-10-23T10:15:30Z"^^xsd:dateTime .
      """;

  private static DockerLocalInstanceRuntime runtime;
  private static Instance server;
  private static RestLdesServerClient client;

  @BeforeAll
  static void startLdesServer() {
    runtime = new DockerLocalInstanceRuntime(new LocalRuntimeProperties(
        "openldes/ldes-server:4.2.1", "openldes/ldi-orchestrator:3.1.1", "postgres:16-alpine",
        Duration.ofMinutes(5), "*/2 * * * * *"));
    LocalRuntimeHandle handle = runtime.start(InstanceType.LDES_SERVER, "it-ldes-server");
    server = Instance.local(InstanceType.LDES_SERVER, "it-ldes-server", handle.url(),
        handle.internalUrl(), handle.runtimeReference(), true, Instant.now());
    client = new RestLdesServerClient(new InstanceRestClients(RestClient.builder()));
  }

  @AfterAll
  static void stopLdesServer() {
    if (server != null) {
      runtime.stop(server.runtimeReference());
    }
  }

  @Test
  void isHealthy() {
    ActuatorHealthProbe probe = new ActuatorHealthProbe(
        new InstanceRestClients(RestClient.builder()), Clock.systemUTC());

    assertThat(probe.check(server).isUp()).isTrue();
  }

  @Test
  void managesTheCatalogOfTheServer() {
    CatalogDescription initial = client.getServerCatalog(server);
    assertThat(initial.catalogId()).isNull();
    assertThat(initial.document().content()).contains("it-ldes-server");

    String catalogId = client.createDcat(server, new DcatTarget.ServerCatalog(null),
        initial.document()).orElseThrow();

    CatalogDescription created = client.getServerCatalog(server);
    assertThat(created.catalogId()).isEqualTo(catalogId);
    assertThat(created.document().content()).contains("it-ldes-server")
        .doesNotContain("identifier");

    client.updateDcat(server, new DcatTarget.ServerCatalog(catalogId), RdfDocument.turtle(
        created.document().content().replace("it-ldes-server", "renamed-server")));
    assertThat(client.getServerCatalog(server).document().content())
        .contains("renamed-server");
  }

  @Test
  void managesEventStreamsViewsAndMembers() {
    EventStream created = client.createEventStream(server, RdfDocument.turtle(EVENT_STREAM));
    assertThat(created.collection()).isEqualTo("it-parcels");
    assertThat(created.timestampPath()).isEqualTo("http://purl.org/dc/terms/created");
    assertThat(client.getEventStreams(server)).extracting(EventStream::collection)
        .contains("it-parcels");

    View view = client.createView(server, "it-parcels", RdfDocument.turtle(VIEW));
    assertThat(view.name()).isEqualTo("by-page");
    assertThat(client.getViews(server, "it-parcels"))
        .filteredOn(candidate -> candidate.name().equals("by-page"))
        .singleElement()
        .satisfies(byPage -> assertThat(byPage.pageSize()).isEqualTo(50));
    assertThat(client.getShape(server, "it-parcels").content()).contains("NodeShape");

    client.ingest(server, "it-parcels", RdfDocument.turtle(MEMBER));
    await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
        assertThat(client.getStatistics(server))
            .filteredOn(statistics -> statistics.collection().equals("it-parcels"))
            .singleElement()
            .satisfies(statistics -> {
              assertThat(statistics.ingestedMembers()).isOne();
              assertThat(statistics.backlog()).isZero();
              assertThat(statistics.views()).singleElement()
                  .satisfies(byPage -> assertThat(byPage.paginatedMembers()).isOne());
            }));

    // The server publishes its IRIs on its host name in the Docker network
    Fragment root = client.getFragment(server, server.resolve("it-parcels/by-page"),
        created.timestampPath(), created.versionOfPath());
    assertThat(root.url()).isEqualTo("http://it-ldes-server:8080/it-parcels/by-page");
    assertThat(root.relations()).singleElement()
        .satisfies(relation -> assertThat(relation.node()).startsWith(root.url()));
    Fragment page = client.getFragment(server,
        server.reachable(root.relations().getFirst().node()), created.timestampPath(),
        created.versionOfPath());
    assertThat(page.members()).singleElement().satisfies(member -> {
      assertThat(member.id()).isEqualTo("http://example.org/parcel/1/2026-10-23");
      assertThat(member.isVersionOf()).isEqualTo("http://example.org/parcel/1");
      assertThat(member.timestamp()).isEqualTo("2026-10-23T10:15:30Z");
      assertThat(member.content().content()).contains("http://example.org/Parcel")
          .doesNotContain("tree:member");
    });

    assertThat(client.getBackgroundProcesses(server))
        .filteredOn(process -> process.job().equals("fragmentation"))
        .extracting(BackgroundProcess::step)
        .contains("bucketisation", "pagination");

    client.deleteView(server, "it-parcels", "by-page");
    client.deleteEventStream(server, "it-parcels");
    assertThat(client.getEventStreams(server)).extracting(EventStream::collection)
        .doesNotContain("it-parcels");
  }
}
