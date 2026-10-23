package org.openldes.ldesadmin.application.service.ldio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.ldio.IllegalPipelineStateException;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.application.service.instance.InstanceLookup;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;

@ExtendWith(MockitoExtension.class)
class PipelineLifecycleServiceTest {

  @Mock
  private InstanceRepository instanceRepository;
  @Mock
  private LdioClient ldioClient;

  private final Instance ldio = TestFixtures.ldio();
  private PipelineLifecycleService service;

  @BeforeEach
  void setUp() {
    service = new PipelineLifecycleService(new InstanceLookup(instanceRepository), ldioClient);
    when(instanceRepository.findById(ldio.id())).thenReturn(Optional.of(ldio));
  }

  @Test
  void haltsARunningPipeline() {
    when(ldioClient.getStatus(ldio, "parcels")).thenReturn(PipelineStatus.RUNNING);
    when(ldioClient.halt(ldio, "parcels")).thenReturn(PipelineStatus.HALTED);

    assertThat(service.halt(ldio.id(), "parcels")).isEqualTo(PipelineStatus.HALTED);
  }

  @Test
  void cannotHaltAPipelineThatIsStillStarting() {
    when(ldioClient.getStatus(ldio, "parcels")).thenReturn(PipelineStatus.INIT);

    assertThatThrownBy(() -> service.halt(ldio.id(), "parcels"))
        .isInstanceOf(IllegalPipelineStateException.class)
        .hasMessage("Cannot halt pipeline parcels, because it is INIT");
    verify(ldioClient, never()).halt(any(), anyString());
  }

  @Test
  void resumesAHaltedPipeline() {
    when(ldioClient.getStatus(ldio, "parcels")).thenReturn(PipelineStatus.HALTED);
    when(ldioClient.resume(ldio, "parcels")).thenReturn(PipelineStatus.RUNNING);

    assertThat(service.resume(ldio.id(), "parcels")).isEqualTo(PipelineStatus.RUNNING);
  }

  @Test
  void cannotResumeARunningPipeline() {
    when(ldioClient.getStatus(ldio, "parcels")).thenReturn(PipelineStatus.RUNNING);

    assertThatThrownBy(() -> service.resume(ldio.id(), "parcels"))
        .isInstanceOf(IllegalPipelineStateException.class);
    verify(ldioClient, never()).resume(any(), anyString());
  }
}
