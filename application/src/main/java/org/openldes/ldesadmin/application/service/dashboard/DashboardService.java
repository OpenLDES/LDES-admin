package org.openldes.ldesadmin.application.service.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.openldes.ldesadmin.application.port.in.dashboard.GetDashboardUseCase;
import org.openldes.ldesadmin.application.port.out.health.InstanceHealthProbe;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.port.out.ldio.LdioClient;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.dashboard.DashboardOverview;
import org.openldes.ldesadmin.model.dashboard.LdesServerSummary;
import org.openldes.ldesadmin.model.dashboard.LdioSummary;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;

@ApplicationService
public class DashboardService implements GetDashboardUseCase {

  private final InstanceRepository instanceRepository;
  private final InstanceHealthProbe instanceHealthProbe;
  private final LdesServerClient ldesServerClient;
  private final LdioClient ldioClient;

  public DashboardService(
      InstanceRepository instanceRepository,
      InstanceHealthProbe instanceHealthProbe,
      LdesServerClient ldesServerClient,
      LdioClient ldioClient) {
    this.instanceRepository = instanceRepository;
    this.instanceHealthProbe = instanceHealthProbe;
    this.ldesServerClient = ldesServerClient;
    this.ldioClient = ldioClient;
  }

  @Override
  public DashboardOverview overview() {
    List<Future<LdesServerSummary>> servers;
    List<Future<LdioSummary>> ldios;
    // Every instance is queried in parallel, so one slow instance doesn't stall the dashboard
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      servers =
          instanceRepository.findByType(InstanceType.LDES_SERVER).stream()
              .map(server -> executor.submit(() -> summarizeLdesServer(server)))
              .toList();
      ldios =
          instanceRepository.findByType(InstanceType.LDIO).stream()
              .map(ldio -> executor.submit(() -> summarizeLdio(ldio)))
              .toList();
    }
    return new DashboardOverview(
        servers.stream().map(Future::resultNow).toList(),
        ldios.stream().map(Future::resultNow).toList());
  }

  private LdesServerSummary summarizeLdesServer(Instance server) {
    InstanceHealth health = instanceHealthProbe.check(server);
    if (!health.isUp()) {
      return new LdesServerSummary(server, health, List.of(), List.of());
    }
    List<String> problems = new ArrayList<>();
    List<StreamStatistics> streams =
        attempt(() -> ldesServerClient.getStatistics(server), problems);
    return new LdesServerSummary(server, health, streams, problems);
  }

  private LdioSummary summarizeLdio(Instance ldio) {
    InstanceHealth health = instanceHealthProbe.check(ldio);
    if (!health.isUp()) {
      return new LdioSummary(ldio, health, List.of(), List.of(), List.of());
    }
    List<String> problems = new ArrayList<>();
    List<DeployedPipeline> pipelines = attempt(() -> ldioClient.getPipelines(ldio), problems);
    List<PipelineStatistics> statistics = attempt(() -> ldioClient.getStatistics(ldio), problems);
    return new LdioSummary(ldio, health, pipelines, statistics, problems);
  }

  private static <T> List<T> attempt(Supplier<List<T>> call, List<String> problems) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      problems.add(e.getMessage());
      return List.of();
    }
  }
}
