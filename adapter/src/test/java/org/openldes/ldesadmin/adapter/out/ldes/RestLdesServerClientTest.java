package org.openldes.ldesadmin.adapter.out.ldes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.AdapterFixtures;
import org.openldes.ldesadmin.adapter.out.http.InstanceRestClients;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException.Reason;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldes.BackgroundProcess;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.Fragment;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldes.View;
import org.openldes.ldesadmin.model.ldes.ViewStatistics;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.rdf.RdfFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestLdesServerClientTest {

  private static final MediaType TURTLE = MediaType.valueOf("text/turtle");

  private final Instance server = AdapterFixtures.ldesServer();
  private MockRestServiceServer mockServer;
  private RestLdesServerClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    client = new RestLdesServerClient(new InstanceRestClients(builder));
  }

  @Test
  void readsTheEventStreamsAndTheirViews() throws IOException {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/eventstreams"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Accept", "text/turtle"))
        .andRespond(withSuccess(resource("ldes/eventstreams.ttl"), TURTLE));

    List<EventStream> streams = client.getEventStreams(server);

    assertThat(streams).extracting(EventStream::collection).containsExactly("addresses", "parcels");
    EventStream parcels = streams.get(1);
    assertThat(parcels.timestampPath()).isEqualTo("http://purl.org/dc/terms/created");
    assertThat(parcels.versionOfPath()).isEqualTo("http://purl.org/dc/terms/isVersionOf");
    assertThat(parcels.createVersions()).isTrue();
    assertThat(parcels.views()).containsExactly("by-page", "by-time");
    assertThat(parcels.definition().content())
        .contains("NodeShape", "pageSize")
        .doesNotContain("addresses");
  }

  @Test
  void readsFragmentationsAndRetentionPoliciesOfViews() throws IOException {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/eventstreams/parcels/views"))
        .andRespond(withSuccess(resource("ldes/views.ttl"), TURTLE));

    List<View> views = client.getViews(server, "parcels");

    assertThat(views).extracting(View::name).containsExactly("by-page", "by-time");
    assertThat(views.getFirst().fragmentations()).isEmpty();
    View byTime = views.get(1);
    assertThat(byTime.pageSize()).isEqualTo(250);
    assertThat(byTime.fragmentations())
        .singleElement()
        .satisfies(
            fragmentation -> {
              assertThat(fragmentation.type()).isEqualTo("HierarchicalTimeBasedFragmentation");
              assertThat(fragmentation.properties())
                  .containsEntry("maxGranularity", "day")
                  .containsEntry("fragmentationPath", "http://purl.org/dc/terms/created");
            });
    assertThat(byTime.retentionPolicies())
        .singleElement()
        .satisfies(
            policy -> {
              assertThat(policy.type()).isEqualTo("DurationAgoPolicy");
              assertThat(policy.properties()).containsEntry("value", "P30D");
            });
  }

  @Test
  void createsAnEventStreamFromItsDefinition() throws IOException {
    String definition = resource("ldes/eventstreams.ttl");
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/eventstreams"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(TURTLE))
        .andExpect(content().string(definition))
        .andRespond(withStatus(HttpStatus.CREATED).contentType(TURTLE).body(definition));

    EventStream created = client.createEventStream(server, RdfDocument.turtle(definition));

    assertThat(created.collection()).isEqualTo("addresses");
  }

  @Test
  void reportsWhyTheServerRejectedARequest() {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/eventstreams"))
        .andRespond(withBadRequest().body("Shape is not valid"));

    RdfDocument definition = RdfDocument.turtle("<a> <b> <c> .");

    assertThatThrownBy(() -> client.createEventStream(server, definition))
        .isInstanceOfSatisfying(
            RemoteInstanceException.class, e -> assertThat(e.reason()).isEqualTo(Reason.REJECTED))
        .hasMessage(
            "LDES server 'Production' could not create event stream: 400 BAD_REQUEST"
                + " - Shape is not valid");
  }

  @Test
  void reportsAnUnknownEventStreamAsNotFound() {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/eventstreams/unknown"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> client.getEventStream(server, "unknown"))
        .isInstanceOfSatisfying(
            RemoteInstanceException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND));
  }

  @Test
  void reportsAnUnreachableServerAsUnavailable() {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/eventstreams"))
        .andRespond(withException(new ConnectException("Connection refused")));

    assertThatThrownBy(() -> client.getEventStreams(server))
        .isInstanceOfSatisfying(
            RemoteInstanceException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE))
        .hasMessageContaining("http://ldes.example.org is unreachable");
  }

  @Test
  void ingestsAMemberOnTheCollectionEndpoint() {
    mockServer
        .expect(requestTo("http://ldes.example.org/parcels"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType("application/n-quads"))
        .andRespond(withSuccess());

    client.ingest(server, "parcels", new RdfDocument("<a> <b> <c> .", RdfFormat.N_QUADS));

    mockServer.verify();
  }

  @Test
  void sendsDcatToTheEndpointOfItsTarget() {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/dcat"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/dcat/catalog-1"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess());
    mockServer
        .expect(
            requestTo(
                "http://ldes.example.org/admin/api/v1/eventstreams/parcels/views/by%20time/dcat"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withSuccess());

    RdfDocument dcat = RdfDocument.turtle("[] a <http://www.w3.org/ns/dcat#Catalog> .");
    client.createDcat(server, new DcatTarget.ServerCatalog(null), dcat);
    client.updateDcat(server, new DcatTarget.ServerCatalog("catalog-1"), dcat);
    client.deleteDcat(server, new DcatTarget.ViewDataService("parcels", "by time"));

    mockServer.verify();
  }

  @Test
  void readsMemberCountsFromTheMetrics() throws IOException {
    mockServer
        .expect(requestTo("http://ldes.example.org/actuator/prometheus"))
        .andRespond(withSuccess(resource("ldes/prometheus.txt"), MediaType.TEXT_PLAIN));

    List<StreamStatistics> statistics = client.getStatistics(server);

    assertThat(statistics)
        .containsExactly(
            new StreamStatistics("addresses", 300, List.of()),
            new StreamStatistics(
                "parcels",
                1200,
                List.of(
                    new ViewStatistics("by-page", 1200, 1200),
                    new ViewStatistics("by-time", 1100, 950))));
  }

  @Test
  void fetchesAPageWithItsRelationsAndMembers() throws IOException {
    URI url = URI.create("http://ldes.example.org/parcels/by-page?pageNumber=1");
    HttpHeaders headers = new HttpHeaders();
    headers.setCacheControl("public,max-age=31536000,immutable");
    mockServer
        .expect(requestTo(url))
        .andExpect(header("Accept", "text/turtle"))
        .andRespond(withSuccess(resource("ldes/fragment-page.ttl"), TURTLE).headers(headers));

    Fragment page =
        client.getFragment(
            server,
            url,
            "http://www.w3.org/ns/prov#generatedAtTime",
            "http://purl.org/dc/terms/isVersionOf");

    assertThat(page.url())
        .isEqualTo("http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=1");
    assertThat(page.immutable()).isTrue();
    assertThat(page.relations())
        .containsExactlyInAnyOrder(
            new Fragment.Relation(
                "Relation",
                null,
                null,
                "http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=2",
                null),
            new Fragment.Relation(
                "GreaterThanOrEqualToRelation",
                "http://www.w3.org/ns/prov#generatedAtTime",
                "2026-10-24T10:02:00Z",
                "http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=3",
                7L));
    assertThat(page.members())
        .extracting(Fragment.Member::id)
        .containsExactly("http://example.org/parcel/1/v1", "http://example.org/parcel/2/v1");
    Fragment.Member second = page.members().get(1);
    assertThat(second.isVersionOf()).isEqualTo("http://example.org/parcel/2");
    assertThat(second.timestamp()).isEqualTo("2026-10-24T10:02:00Z");
    assertThat(second.content().content())
        .contains("POINT (4.4 51.2)", "PREFIX dcterms:")
        .doesNotContain("parcel/1/v1", "tree:", "PREFIX sh:");
  }

  @Test
  void readsTheCatalogOfTheServerWithoutWhatTheServerAdds() throws IOException {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/dcat"))
        .andRespond(withSuccess(resource("ldes/dcat.ttl"), TURTLE));

    CatalogDescription catalog = client.getServerCatalog(server);

    assertThat(catalog.catalogId()).isEqualTo("1d911b66-e610-41a5-88a5-65823acbb9cd");
    assertThat(catalog.document().content())
        .contains("dcat:Catalog", "Production catalog", "Digitaal Vlaanderen")
        .doesNotContain("identifier", "dataset", "Parcels", "ldes.example.org");
  }

  @Test
  void proposesACatalogWhenTheServerHasNone() {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/dcat"))
        .andRespond(withSuccess("", TURTLE));

    CatalogDescription catalog = client.getServerCatalog(server);

    assertThat(catalog.catalogId()).isNull();
    assertThat(catalog.document().content()).contains("[] a dcat:Catalog", "\"Production\"@en");
  }

  @Test
  void returnsTheIdOfACreatedServerCatalog() {
    mockServer
        .expect(requestTo("http://ldes.example.org/admin/api/v1/dcat"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withStatus(HttpStatus.CREATED)
                .contentType(TURTLE)
                .body("1d911b66-e610-41a5-88a5-65823acbb9cd"));

    assertThat(
            client.createDcat(
                server,
                new DcatTarget.ServerCatalog(null),
                RdfDocument.turtle("[] a <http://www.w3.org/ns/dcat#Catalog> .")))
        .contains("1d911b66-e610-41a5-88a5-65823acbb9cd");
  }

  @Test
  void readsTheBackgroundProcessesFromTheBatchMetrics() throws IOException {
    mockServer
        .expect(requestTo("http://ldes.example.org/actuator/prometheus"))
        .andRespond(withSuccess(resource("ldes/prometheus.txt"), MediaType.TEXT_PLAIN));

    assertThat(client.getBackgroundProcesses(server))
        .containsExactly(
            new BackgroundProcess("fragmentation", "bucketisation", 4, 0, 50, 51, false),
            new BackgroundProcess("fragmentation", "pagination", 2, 1, 19, 34, true));
  }

  private static String resource(String path) throws IOException {
    return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
  }
}
