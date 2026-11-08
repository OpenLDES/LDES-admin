package org.openldes.ldesadmin.application.service.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.out.health.InstanceHealthProbe;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.port.out.monitoring.IngestSampleRepository;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime;
import org.openldes.ldesadmin.application.port.out.runtime.LocalRuntimeException;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;
import org.openldes.ldesadmin.model.monitoring.IngestSample;

@ExtendWith(MockitoExtension.class)
class InstanceMonitorServiceTest {

  @Mock private InstanceRepository instanceRepository;
  @Mock private InstanceHealthProbe instanceHealthProbe;
  @Mock private LdesServerClient ldesServerClient;
  @Mock private IngestSampleRepository ingestSamples;
  @Mock private LocalInstanceRuntime localInstanceRuntime;
  @Mock private ActivityRecorder activity;

  private final Instance server = TestFixtures.ldesServer();
  private InstanceMonitorService service;

  @BeforeEach
  void setUp() {
    service =
        new InstanceMonitorService(
            instanceRepository,
            instanceHealthProbe,
            ldesServerClient,
            ingestSamples,
            localInstanceRuntime,
            activity,
            TestFixtures.CLOCK);
  }

  @Test
  void samplesTheMembersIngestedByAHealthyServer() {
    givenInstances(List.of(server), List.of());
    when(instanceHealthProbe.check(server)).thenReturn(InstanceHealth.up(TestFixtures.NOW));
    when(ldesServerClient.getStatistics(server))
        .thenReturn(
            List.of(
                new StreamStatistics("parcels", 1_200, List.of()),
                new StreamStatistics("addresses", 300, List.of())));

    service.monitor();

    verify(ingestSamples).save(new IngestSample(server.id(), TestFixtures.NOW, 1_500));
    verify(ingestSamples)
        .deleteBefore(TestFixtures.NOW.minus(InstanceMonitorService.SAMPLE_RETENTION));
  }

  @Test
  void recordsWhenAnInstanceGoesDownAndComesBack() {
    Instance ldio = TestFixtures.ldio();
    givenInstances(List.of(), List.of(ldio));
    when(instanceHealthProbe.check(ldio))
        .thenReturn(
            InstanceHealth.up(TestFixtures.NOW),
            InstanceHealth.down(TestFixtures.NOW),
            InstanceHealth.down(TestFixtures.NOW),
            InstanceHealth.up(TestFixtures.NOW));

    service.monitor();
    verifyNoInteractions(activity);

    service.monitor();
    verify(activity).record(eq(ldio), eq(Severity.ERROR), eq("LDIO is down"), anyString());

    service.monitor();
    service.monitor();
    verify(activity).success(ldio, "LDIO is up again");
  }

  @Test
  void doesNotRecordAnInstanceThatIsAlreadyDownAtTheFirstCheck() {
    givenInstances(List.of(server), List.of());
    when(instanceHealthProbe.check(server)).thenReturn(InstanceHealth.down(TestFixtures.NOW));

    service.monitor();

    verifyNoInteractions(activity, ldesServerClient);
    verify(ingestSamples, never()).save(any());
  }

  @Test
  void removesLocalInstancesThatNoLongerRun() {
    Instance stopped = TestFixtures.localLdio();
    Instance running = TestFixtures.localLdesServer();
    givenInstances(List.of(server, running), List.of(stopped));
    when(localInstanceRuntime.isRunning("container-42")).thenReturn(false);
    when(localInstanceRuntime.isRunning("container-43")).thenReturn(true);

    assertThat(service.removeStoppedLocalInstances()).isOne();

    verify(instanceRepository).deleteById(stopped.id());
    verify(instanceRepository, never()).deleteById(running.id());
    verify(instanceRepository, never()).deleteById(server.id());
  }

  @Test
  void keepsLocalInstancesWhenTheRuntimeCannotTell() {
    givenInstances(List.of(), List.of(TestFixtures.localLdio()));
    when(localInstanceRuntime.isRunning("container-42"))
        .thenThrow(new LocalRuntimeException("No Docker", null));

    assertThat(service.removeStoppedLocalInstances()).isZero();

    verify(instanceRepository, never()).deleteById(any());
  }

  private void givenInstances(List<Instance> servers, List<Instance> ldios) {
    when(instanceRepository.findByType(InstanceType.LDES_SERVER)).thenReturn(servers);
    when(instanceRepository.findByType(InstanceType.LDIO)).thenReturn(ldios);
  }
}
