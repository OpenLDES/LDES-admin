package org.openldes.ldesadmin.application.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException.Reason;
import org.openldes.ldesadmin.application.port.out.health.InstanceHealthProbe;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.model.dashboard.DashboardOverview;
import org.openldes.ldesadmin.model.dashboard.LdioSummary;
import org.openldes.ldesadmin.model.instance.HealthStatus;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock
  private InstanceRepository instanceRepository;
  @Mock
  private InstanceHealthProbe instanceHealthProbe;
  @Mock
  private LdesServerClient ldesServerClient;
  @Mock
  private LdioClient ldioClient;

  private DashboardService service;

  @BeforeEach
  void setUp() {
    service = new DashboardService(instanceRepository, instanceHealthProbe, ldesServerClient,
        ldioClient);
  }

  @Test
  void summarizesEveryInstance() {
    Instance server = TestFixtures.ldesServer();
    Instance ldio = TestFixtures.ldio();
    when(instanceRepository.findByType(InstanceType.LDES_SERVER)).thenReturn(List.of(server));
    when(instanceRepository.findByType(InstanceType.LDIO)).thenReturn(List.of(ldio));
    when(instanceHealthProbe.check(server)).thenReturn(InstanceHealth.up(TestFixtures.NOW));
    when(instanceHealthProbe.check(ldio)).thenReturn(InstanceHealth.up(TestFixtures.NOW));
    when(ldesServerClient.getStatistics(server)).thenReturn(List.of(
        new StreamStatistics("parcels", 1_200, List.of()),
        new StreamStatistics("addresses", 300, List.of())));
    when(ldioClient.getPipelines(ldio)).thenReturn(List.of(
        TestFixtures.deployed("parcels", PipelineStatus.RUNNING)));
    when(ldioClient.getStatistics(ldio)).thenReturn(List.of(
        new PipelineStatistics("parcels", 50, 48)));

    DashboardOverview overview = service.overview();

    assertThat(overview.ldesServers()).singleElement().satisfies(summary -> {
      assertThat(summary.server()).isEqualTo(server);
      assertThat(summary.ingestedMembers()).isEqualTo(1_500);
      assertThat(summary.problems()).isEmpty();
    });
    assertThat(overview.ldios()).singleElement().satisfies(summary -> {
      assertThat(summary.pipelinesByStatus()).containsEntry(PipelineStatus.RUNNING, 1L);
      assertThat(summary.processedMembers()).isEqualTo(48);
    });
  }

  @Test
  void doesNotQueryAnInstanceThatIsDown() {
    Instance server = TestFixtures.ldesServer();
    when(instanceRepository.findByType(InstanceType.LDES_SERVER)).thenReturn(List.of(server));
    when(instanceRepository.findByType(InstanceType.LDIO)).thenReturn(List.of());
    when(instanceHealthProbe.check(server)).thenReturn(InstanceHealth.down(TestFixtures.NOW));

    DashboardOverview overview = service.overview();

    assertThat(overview.ldesServers()).singleElement()
        .satisfies(summary -> assertThat(summary.health().status()).isEqualTo(HealthStatus.DOWN));
    verifyNoInteractions(ldesServerClient);
  }

  @Test
  void reportsProblemsInsteadOfFailing() {
    Instance ldio = TestFixtures.ldio();
    when(instanceRepository.findByType(InstanceType.LDES_SERVER)).thenReturn(List.of());
    when(instanceRepository.findByType(InstanceType.LDIO)).thenReturn(List.of(ldio));
    when(instanceHealthProbe.check(ldio)).thenReturn(InstanceHealth.up(TestFixtures.NOW));
    when(ldioClient.getPipelines(ldio)).thenReturn(List.of(
        TestFixtures.deployed("parcels", PipelineStatus.RUNNING)));
    when(ldioClient.getStatistics(ldio)).thenThrow(new RemoteInstanceException(
        Reason.NOT_FOUND, "Metrics are not exposed"));

    LdioSummary summary = service.overview().ldios().getFirst();

    assertThat(summary.pipelines()).hasSize(1);
    assertThat(summary.statistics()).isEmpty();
    assertThat(summary.problems()).containsExactly("Metrics are not exposed");
  }
}
