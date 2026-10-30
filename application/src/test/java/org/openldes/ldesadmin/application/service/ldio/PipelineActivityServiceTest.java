package org.openldes.ldesadmin.application.service.ldio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineNotFoundException;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.openldes.ldesadmin.model.ldio.StatusChangeSource;

@ExtendWith(MockitoExtension.class)
class PipelineActivityServiceTest {

  private static final String MEMBER = "<a> <b> <c> .";
  private static final String TURTLE = "text/turtle";

  @Mock private InstanceRepository instanceRepository;
  @Mock private LdioClient ldioClient;

  private final Instance ldio = TestFixtures.ldio();
  private PipelineActivityService service;

  @BeforeEach
  void setUp() {
    service = new PipelineActivityService(new InstanceLookup(instanceRepository), ldioClient);
  }

  @Test
  void sendsAMemberToAPipelineWithAnHttpInput() {
    givenLdioExists();
    when(ldioClient.getPipelines(ldio))
        .thenReturn(List.of(TestFixtures.deployed("parcels", PipelineStatus.RUNNING)));

    service.send(ldio.id(), "parcels", MEMBER, TURTLE);

    verify(ldioClient).send(ldio, "parcels", MEMBER, TURTLE);
  }

  @Test
  void doesNotSendToAPipelineThatReadsFromAnLdes() {
    givenLdioExists();
    Pipeline client =
        new Pipeline(
            "replicate",
            null,
            new InputDefinition(
                "Ldio:LdesClient", Map.of("urls", "http://ldes.example.org/parcels"), null),
            null,
            List.of(ComponentDefinition.of("Ldio:ConsoleOut")));
    when(ldioClient.getPipelines(ldio))
        .thenReturn(
            List.of(new DeployedPipeline(client, PipelineStatus.RUNNING, StatusChangeSource.AUTO)));
    InstanceId ldioId = ldio.id();

    assertThatThrownBy(() -> service.send(ldioId, "replicate", MEMBER, TURTLE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ldio:LdesClient");
    verify(ldioClient, never()).send(any(), any(), any(), any());
  }

  @Test
  void reportsAnUnknownPipeline() {
    givenLdioExists();
    when(ldioClient.getPipelines(ldio)).thenReturn(List.of());
    InstanceId ldioId = ldio.id();

    assertThatThrownBy(() -> service.send(ldioId, "parcels", MEMBER, TURTLE))
        .isInstanceOf(PipelineNotFoundException.class);
  }

  @Test
  void refusesAnEmptyMessage() {
    InstanceId ldioId = ldio.id();

    assertThatThrownBy(() -> service.send(ldioId, "parcels", " ", "application/json"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(ldioClient, never()).send(any(), any(), any(), any());
  }

  private void givenLdioExists() {
    when(instanceRepository.findById(ldio.id())).thenReturn(Optional.of(ldio));
  }
}
