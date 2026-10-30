package org.openldes.ldesadmin.application.service.ldes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.instance.InstanceNotFoundException;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.application.service.monitoring.ActivityRecorder;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.Fragment;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

@ExtendWith(MockitoExtension.class)
class LdesServerServicesTest {

  private static final RdfDocument TURTLE = RdfDocument.turtle("<a> <b> <c> .");

  @Mock private InstanceRepository instanceRepository;
  @Mock private LdesServerClient ldesServerClient;
  @Mock private ActivityRecorder activity;

  private final Instance server = TestFixtures.ldesServer();
  private InstanceLookup instanceLookup;

  @BeforeEach
  void setUp() {
    instanceLookup = new InstanceLookup(instanceRepository);
  }

  @Test
  void listsTheEventStreamsOfTheServer() {
    givenServerExists();
    EventStream parcels = new EventStream("parcels", null, null, false, List.of(), TURTLE);
    when(ldesServerClient.getEventStreams(server)).thenReturn(List.of(parcels));

    EventStreamService service = new EventStreamService(instanceLookup, ldesServerClient, activity);

    assertThat(service.list(server.id())).containsExactly(parcels);
  }

  @Test
  void failsForAnUnknownServer() {
    EventStreamService service = new EventStreamService(instanceLookup, ldesServerClient, activity);
    InstanceId unknown = InstanceId.randomId();

    assertThatThrownBy(() -> service.list(unknown)).isInstanceOf(InstanceNotFoundException.class);
    verifyNoInteractions(ldesServerClient);
  }

  @Test
  void ingestsAMember() {
    givenServerExists();
    IngestMemberService service = new IngestMemberService(instanceLookup, ldesServerClient);

    service.ingest(server.id(), "parcels", TURTLE);

    verify(ldesServerClient).ingest(server, "parcels", TURTLE);
  }

  @Test
  void refusesToIngestAnEmptyMember() {
    IngestMemberService service = new IngestMemberService(instanceLookup, ldesServerClient);
    InstanceId serverId = server.id();
    RdfDocument empty = RdfDocument.turtle("  ");

    assertThatThrownBy(() -> service.ingest(serverId, "parcels", empty))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(ldesServerClient);
  }

  @Test
  void updatingTheServerCatalogRequiresItsId() {
    DcatService service = new DcatService(instanceLookup, ldesServerClient);
    InstanceId serverId = server.id();
    DcatTarget withoutId = new DcatTarget.ServerCatalog(null);

    assertThatThrownBy(() -> service.update(serverId, withoutId, TURTLE))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(ldesServerClient);
  }

  @Test
  void updatesTheDatasetOfAnEventStream() {
    givenServerExists();
    DcatService service = new DcatService(instanceLookup, ldesServerClient);
    DcatTarget dataset = new DcatTarget.EventStreamDataset("parcels");

    service.update(server.id(), dataset, TURTLE);

    verify(ldesServerClient).updateDcat(server, dataset, TURTLE);
  }

  @Test
  void recordsACreatedEventStream() {
    givenServerExists();
    EventStream parcels = new EventStream("parcels", null, null, false, List.of(), TURTLE);
    when(ldesServerClient.createEventStream(server, TURTLE)).thenReturn(parcels);
    EventStreamService service = new EventStreamService(instanceLookup, ldesServerClient, activity);

    service.create(server.id(), TURTLE);

    verify(activity).success(server, "Event stream parcels created");
  }

  @Test
  void createsTheServerCatalogWhenThereIsNoneYet() {
    givenServerExists();
    when(ldesServerClient.getServerCatalog(server))
        .thenReturn(new CatalogDescription(null, RdfDocument.turtle("")));
    when(ldesServerClient.createDcat(server, new DcatTarget.ServerCatalog(null), TURTLE))
        .thenReturn(Optional.of("catalog-1"));
    DcatService service = new DcatService(instanceLookup, ldesServerClient);

    assertThat(service.saveServerCatalog(server.id(), TURTLE)).isEqualTo("catalog-1");
  }

  @Test
  void updatesTheExistingServerCatalog() {
    givenServerExists();
    when(ldesServerClient.getServerCatalog(server))
        .thenReturn(new CatalogDescription("catalog-1", TURTLE));
    DcatService service = new DcatService(instanceLookup, ldesServerClient);

    assertThat(service.saveServerCatalog(server.id(), TURTLE)).isEqualTo("catalog-1");

    verify(ldesServerClient).updateDcat(server, new DcatTarget.ServerCatalog("catalog-1"), TURTLE);
    verify(ldesServerClient, never()).createDcat(any(), any(), any());
  }

  @Test
  void fetchesTheRootFragmentOfAView() {
    givenServerExists();
    givenParcelsStream();
    Fragment root = new Fragment("http://ldes.example.org/parcels/by-page", false, null, null);
    when(ldesServerClient.getFragment(
            server,
            URI.create("http://ldes.example.org/parcels/by-page"),
            "prov:generatedAtTime",
            "dcterms:isVersionOf"))
        .thenReturn(root);

    assertThat(fragmentService().fetch(server.id(), "parcels", "by-page", null)).isEqualTo(root);
  }

  @Test
  void fetchesAFragmentALocalServerPublishedOnItsInternalUrl() {
    Instance local = TestFixtures.localLdesServer();
    when(instanceRepository.findById(local.id())).thenReturn(Optional.of(local));
    when(ldesServerClient.getEventStream(local, "parcels")).thenReturn(parcelsStream());

    fragmentService()
        .fetch(
            local.id(),
            "parcels",
            "by-page",
            "http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=2");

    verify(ldesServerClient)
        .getFragment(
            local,
            URI.create("http://localhost:32769/parcels/by-page?pageNumber=2"),
            "prov:generatedAtTime",
            "dcterms:isVersionOf");
  }

  @Test
  void refusesToFetchFromAnotherHost() {
    givenServerExists();
    givenParcelsStream();
    InstanceId serverId = server.id();
    FragmentService service = fragmentService();

    assertThatThrownBy(
            () ->
                service.fetch(
                    serverId,
                    "parcels",
                    "by-page",
                    "http://ldes.example.org.evil.com/parcels/by-page"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(ldesServerClient, never()).getFragment(any(), any(), any(), any());
  }

  private FragmentService fragmentService() {
    return new FragmentService(instanceLookup, ldesServerClient);
  }

  private void givenParcelsStream() {
    when(ldesServerClient.getEventStream(server, "parcels")).thenReturn(parcelsStream());
  }

  private static EventStream parcelsStream() {
    return new EventStream(
        "parcels",
        "prov:generatedAtTime",
        "dcterms:isVersionOf",
        false,
        List.of("by-page"),
        TURTLE);
  }

  private void givenServerExists() {
    when(instanceRepository.findById(server.id())).thenReturn(Optional.of(server));
  }
}
