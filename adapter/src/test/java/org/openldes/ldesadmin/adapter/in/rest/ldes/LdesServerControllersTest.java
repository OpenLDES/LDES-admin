package org.openldes.ldesadmin.adapter.in.rest.ldes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.application.port.in.ldes.BackgroundProcessUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.DcatUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.EventStreamUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.FragmentUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.IngestMemberUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.ShapeUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.StreamStatisticsUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.ViewUseCase;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException.Reason;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.BackgroundProcess;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.Fragment;
import org.openldes.ldesadmin.model.ldes.Fragment.Relation;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldes.ViewStatistics;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.rdf.RdfFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = {EventStreamController.class, ViewController.class,
    ShapeController.class, DcatController.class, MemberController.class,
    FragmentController.class})
class LdesServerControllersTest {

  private static final String TURTLE = "text/turtle";
  private static final InstanceId SERVER = InstanceId.of("6f3c1c1e-3f0a-4d44-9d1c-2a8d0e7c9b11");

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private EventStreamUseCase eventStreamUseCase;
  @MockitoBean
  private ViewUseCase viewUseCase;
  @MockitoBean
  private ShapeUseCase shapeUseCase;
  @MockitoBean
  private DcatUseCase dcatUseCase;
  @MockitoBean
  private IngestMemberUseCase ingestMemberUseCase;
  @MockitoBean
  private StreamStatisticsUseCase streamStatisticsUseCase;
  @MockitoBean
  private BackgroundProcessUseCase backgroundProcessUseCase;
  @MockitoBean
  private FragmentUseCase fragmentUseCase;

  @Test
  void fetchesTheRootOfAView() {
    when(fragmentUseCase.fetch(SERVER, "parcels", "by-page", null)).thenReturn(new Fragment(
        "http://sandbox-ldes-server:8080/parcels/by-page", false,
        List.of(new Relation("Relation", null, null,
            "http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=1", null)),
        List.of()));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/eventstreams/parcels/views/by-page"
        + "/fragment", SERVER))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            {"url": "http://sandbox-ldes-server:8080/parcels/by-page", "collection": "parcels",
             "view": "by-page", "root": true, "immutable": false, "members": [],
             "relations": [{"type": "Relation",
               "node": "http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=1"}]}
            """);
  }

  @Test
  void fetchesAPageWithItsMembers() {
    String page = "http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=1";
    when(fragmentUseCase.fetch(SERVER, "parcels", "by-page", page)).thenReturn(new Fragment(
        page, true, List.of(), List.of(new Fragment.Member("http://example.org/parcel/1/v1",
            "http://example.org/parcel/1", "2026-10-24T10:01:00Z",
            RdfDocument.turtle("<http://example.org/parcel/1/v1> a <Parcel> .")))));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/eventstreams/parcels/views/by-page"
        + "/fragment", SERVER).param("url", page))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            {"root": false, "immutable": true,
             "members": [{"id": "http://example.org/parcel/1/v1",
               "isVersionOf": "http://example.org/parcel/1",
               "timestamp": "2026-10-24T10:01:00Z",
               "content": "<http://example.org/parcel/1/v1> a <Parcel> ."}]}
            """);
  }

  @Test
  void refusesToFetchFromAnotherHost() {
    when(fragmentUseCase.fetch(SERVER, "parcels", "by-page", "http://evil.example.org"))
        .thenThrow(new IllegalArgumentException("not a fragment of this server"));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/eventstreams/parcels/views/by-page"
        + "/fragment", SERVER).param("url", "http://evil.example.org"))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void reportsTheBackgroundProcesses() {
    when(backgroundProcessUseCase.processes(SERVER)).thenReturn(List.of(
        new BackgroundProcess("fragmentation", "pagination", 12, 1, 40, 120, true)));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/processes", SERVER))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            [{"job": "fragmentation", "step": "pagination", "completedRuns": 12,
              "failedRuns": 1, "averageMillis": 40, "longestMillis": 120, "running": true}]
            """);
  }

  @Test
  void servesTheServerCatalogForEditing() {
    when(dcatUseCase.getServerCatalog(SERVER)).thenReturn(new CatalogDescription(null,
        RdfDocument.turtle("[] a dcat:Catalog .")));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/server-catalog", SERVER))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            {"catalogId": null, "document": "[] a dcat:Catalog ."}
            """);
  }

  @Test
  void savesTheServerCatalog() {
    when(dcatUseCase.saveServerCatalog(SERVER, RdfDocument.turtle("[] a dcat:Catalog .")))
        .thenReturn("catalog-1");

    assertThat(mvc.put().uri("/api/v1/ldes-servers/{id}/server-catalog", SERVER)
        .contentType(TURTLE).content("[] a dcat:Catalog ."))
        .hasStatusOk()
        .bodyJson().extractingPath("$.catalogId").isEqualTo("catalog-1");
  }

  @Test
  void listsEventStreams() {
    when(eventStreamUseCase.list(SERVER)).thenReturn(List.of(new EventStream("parcels",
        "http://purl.org/dc/terms/created", "http://purl.org/dc/terms/isVersionOf", true,
        List.of("by-page"), RdfDocument.turtle("<parcels> a <EventStream> ."))));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/eventstreams", SERVER))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            [{"collection": "parcels", "createVersions": true, "views": ["by-page"],
              "definition": "<parcels> a <EventStream> ."}]
            """);
  }

  @Test
  void createsAnEventStreamFromTurtle() {
    when(eventStreamUseCase.create(eq(SERVER), any())).thenReturn(new EventStream("parcels",
        null, null, false, List.of(), RdfDocument.turtle("<parcels> a <EventStream> .")));

    assertThat(mvc.post().uri("/api/v1/ldes-servers/{id}/eventstreams", SERVER)
        .contentType(TURTLE)
        .content("<parcels> a <EventStream> ."))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson().extractingPath("$.collection").isEqualTo("parcels");

    verify(eventStreamUseCase).create(SERVER, RdfDocument.turtle("<parcels> a <EventStream> ."));
  }

  @Test
  void onlyAcceptsRdf() {
    assertThat(mvc.post().uri("/api/v1/ldes-servers/{id}/eventstreams", SERVER)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
        .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

    verifyNoInteractions(eventStreamUseCase);
  }

  @Test
  void reportsRejectionsByTheServerAsBadRequest() {
    when(eventStreamUseCase.create(eq(SERVER), any())).thenThrow(new RemoteInstanceException(
        Reason.REJECTED, "LDES server 'Production' could not create event stream: 400"));

    assertThat(mvc.post().uri("/api/v1/ldes-servers/{id}/eventstreams", SERVER)
        .contentType(TURTLE)
        .content("not turtle"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson().extractingPath("$.detail")
        .isEqualTo("LDES server 'Production' could not create event stream: 400");
  }

  @Test
  void reportsAnUnreachableServerAsBadGateway() {
    when(eventStreamUseCase.list(SERVER)).thenThrow(new RemoteInstanceException(
        Reason.UNAVAILABLE, "unreachable"));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/eventstreams", SERVER))
        .hasStatus(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void servesTheShapeAsTurtle() {
    when(shapeUseCase.get(SERVER, "parcels")).thenReturn(RdfDocument.turtle(
        "[] a <http://www.w3.org/ns/shacl#NodeShape> ."));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/eventstreams/parcels/shape", SERVER))
        .hasStatusOk()
        .hasContentTypeCompatibleWith(TURTLE)
        .hasBodyTextEqualTo("[] a <http://www.w3.org/ns/shacl#NodeShape> .");
  }

  @Test
  void ingestsAMemberInAnyRdfFormat() {
    assertThat(mvc.post().uri("/api/v1/ldes-servers/{id}/eventstreams/parcels/members", SERVER)
        .contentType("application/n-quads")
        .content("<a> <b> <c> ."))
        .hasStatus(HttpStatus.ACCEPTED);

    verify(ingestMemberUseCase).ingest(SERVER, "parcels",
        new RdfDocument("<a> <b> <c> .", RdfFormat.N_QUADS));
  }

  @Test
  void updatesTheDcatOfAView() {
    assertThat(mvc.put()
        .uri("/api/v1/ldes-servers/{id}/eventstreams/parcels/views/by-page/dcat", SERVER)
        .contentType(TURTLE)
        .content("[] a <http://www.w3.org/ns/dcat#DataService> ."))
        .hasStatus(HttpStatus.NO_CONTENT);

    verify(dcatUseCase).update(SERVER, new DcatTarget.ViewDataService("parcels", "by-page"),
        RdfDocument.turtle("[] a <http://www.w3.org/ns/dcat#DataService> ."));
  }

  @Test
  void reportsStatisticsPerStream() {
    when(streamStatisticsUseCase.statistics(SERVER)).thenReturn(List.of(new StreamStatistics(
        "parcels", 1200, List.of(new ViewStatistics("by-time", 1100, 950)))));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/statistics", SERVER))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            [{"collection": "parcels", "ingestedMembers": 1200, "backlog": 150,
              "views": [{"view": "by-time", "bucketisedMembers": 1100, "paginatedMembers": 950}]}]
            """);
  }
}
