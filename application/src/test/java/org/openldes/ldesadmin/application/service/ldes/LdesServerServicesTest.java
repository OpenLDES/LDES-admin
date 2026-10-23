package org.openldes.ldesadmin.application.service.ldes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

@ExtendWith(MockitoExtension.class)
class LdesServerServicesTest {

  private static final RdfDocument TURTLE = RdfDocument.turtle("<a> <b> <c> .");

  @Mock
  private InstanceRepository instanceRepository;
  @Mock
  private LdesServerClient ldesServerClient;

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

    EventStreamService service = new EventStreamService(instanceLookup, ldesServerClient);

    assertThat(service.list(server.id())).containsExactly(parcels);
  }

  @Test
  void failsForAnUnknownServer() {
    EventStreamService service = new EventStreamService(instanceLookup, ldesServerClient);
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

  private void givenServerExists() {
    when(instanceRepository.findById(server.id())).thenReturn(Optional.of(server));
  }
}
