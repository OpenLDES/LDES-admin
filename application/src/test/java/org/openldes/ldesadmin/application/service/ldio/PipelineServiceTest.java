package org.openldes.ldesadmin.application.service.ldio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.instance.InstanceNotFoundException;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineNotFoundException;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

  @Mock
  private InstanceRepository instanceRepository;
  @Mock
  private LdioClient ldioClient;

  private final Instance ldio = TestFixtures.ldio();
  private PipelineService service;

  @BeforeEach
  void setUp() {
    service = new PipelineService(new InstanceLookup(instanceRepository), ldioClient);
  }

  @Test
  void findsADeployedPipelineByName() {
    givenLdioExists();
    DeployedPipeline parcels = TestFixtures.deployed("parcels", PipelineStatus.RUNNING);
    when(ldioClient.getPipelines(ldio)).thenReturn(List.of(
        TestFixtures.deployed("addresses", PipelineStatus.HALTED), parcels));

    assertThat(service.get(ldio.id(), "parcels")).isEqualTo(parcels);
  }

  @Test
  void reportsAnUnknownPipeline() {
    givenLdioExists();
    when(ldioClient.getPipelines(ldio)).thenReturn(List.of());

    assertThatThrownBy(() -> service.get(ldio.id(), "parcels"))
        .isInstanceOf(PipelineNotFoundException.class)
        .hasMessageContaining("parcels");
  }

  @Test
  void updatesAPipelineByRecreatingIt() {
    givenLdioExists();
    Pipeline updated = TestFixtures.pipeline("parcels");
    DeployedPipeline redeployed = TestFixtures.deployed("parcels", PipelineStatus.INIT);
    when(ldioClient.getPipelines(ldio)).thenReturn(List.of(
        TestFixtures.deployed("parcels", PipelineStatus.RUNNING)));
    when(ldioClient.createPipeline(ldio, updated)).thenReturn(redeployed);

    assertThat(service.update(ldio.id(), updated)).isEqualTo(redeployed);

    InOrder inOrder = inOrder(ldioClient);
    inOrder.verify(ldioClient).deletePipeline(ldio, "parcels");
    inOrder.verify(ldioClient).createPipeline(ldio, updated);
  }

  @Test
  void doesNotUpdateAPipelineThatDoesNotExist() {
    givenLdioExists();
    when(ldioClient.getPipelines(ldio)).thenReturn(List.of());
    Pipeline pipeline = TestFixtures.pipeline("parcels");

    assertThatThrownBy(() -> service.update(ldio.id(), pipeline))
        .isInstanceOf(PipelineNotFoundException.class);
    verify(ldioClient, never()).deletePipeline(any(), anyString());
    verify(ldioClient, never()).createPipeline(any(), any());
  }

  @Test
  void onlyTalksToLdios() {
    Instance server = TestFixtures.ldesServer();
    when(instanceRepository.findById(server.id())).thenReturn(Optional.of(server));

    assertThatThrownBy(() -> service.list(server.id()))
        .isInstanceOf(InstanceNotFoundException.class);
  }

  private void givenLdioExists() {
    when(instanceRepository.findById(ldio.id())).thenReturn(Optional.of(ldio));
  }
}
