package org.openldes.ldesadmin.application.service.monitoring;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openldes.ldesadmin.application.port.in.monitoring.MonitorInstancesUseCase;
import org.openldes.ldesadmin.application.port.out.health.InstanceHealthProbe;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.application.port.out.monitoring.IngestSampleRepository;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.HealthStatus;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;
import org.openldes.ldesadmin.model.monitoring.IngestSample;

@ApplicationService
public class InstanceMonitorService implements MonitorInstancesUseCase {

  /**
   * How long ingest samples are kept: long enough to derive the throughput of the last 24 hours.
   */
  static final Duration SAMPLE_RETENTION = Duration.ofHours(48);

  private static final Logger log = System.getLogger(InstanceMonitorService.class.getName());

  private final InstanceRepository instanceRepository;
  private final InstanceHealthProbe instanceHealthProbe;
  private final LdesServerClient ldesServerClient;
  private final IngestSampleRepository ingestSamples;
  private final LocalInstanceRuntime localInstanceRuntime;
  private final ActivityRecorder activity;
  private final Clock clock;

  /**
   * The health of every instance at the previous check, to notice when it changes.
   */
  private final Map<InstanceId, HealthStatus> lastStatus = new ConcurrentHashMap<>();

  public InstanceMonitorService(InstanceRepository instanceRepository,
      InstanceHealthProbe instanceHealthProbe, LdesServerClient ldesServerClient,
      IngestSampleRepository ingestSamples, LocalInstanceRuntime localInstanceRuntime,
      ActivityRecorder activity, Clock clock) {
    this.instanceRepository = instanceRepository;
    this.instanceHealthProbe = instanceHealthProbe;
    this.ldesServerClient = ldesServerClient;
    this.ingestSamples = ingestSamples;
    this.localInstanceRuntime = localInstanceRuntime;
    this.activity = activity;
    this.clock = clock;
  }

  @Override
  public void monitor() {
    List<Instance> instances = allInstances();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      instances.forEach(instance -> executor.submit(() -> monitor(instance)));
    }
    Set<InstanceId> known = instances.stream().map(Instance::id).collect(Collectors.toSet());
    lastStatus.keySet().retainAll(known);
    ingestSamples.deleteBefore(clock.instant().minus(SAMPLE_RETENTION));
  }

  @Override
  public int removeStoppedLocalInstances() {
    int removed = 0;
    for (Instance instance : allInstances()) {
      if (instance.isLocal() && hasStopped(instance)) {
        instanceRepository.deleteById(instance.id());
        activity.record(instance, Severity.INFO, "Local " + instance.type().label() + " removed",
            "It stopped together with the LDES-admin run that started it");
        removed++;
      }
    }
    return removed;
  }

  private void monitor(Instance instance) {
    try {
      InstanceHealth health = instanceHealthProbe.check(instance);
      recordChange(instance, lastStatus.put(instance.id(), health.status()), health.status());
      if (health.isUp() && instance.type() == InstanceType.LDES_SERVER) {
        sampleIngestedMembers(instance);
      }
    } catch (RuntimeException e) {
      log.log(Level.WARNING, "Could not monitor " + instance.name(), e);
    }
  }

  /**
   * Only records changes: an instance that is already down when LDES-admin starts is not news.
   */
  private void recordChange(Instance instance, HealthStatus previous, HealthStatus current) {
    String label = instance.type().label();
    if (previous == HealthStatus.UP && current != HealthStatus.UP) {
      activity.record(instance, Severity.ERROR, label + " is down",
          "It no longer responds healthy on " + instance.url());
    } else if (previous != null && previous != HealthStatus.UP && current == HealthStatus.UP) {
      activity.success(instance, label + " is up again");
    }
  }

  private void sampleIngestedMembers(Instance server) {
    long ingested = ldesServerClient.getStatistics(server).stream()
        .mapToLong(StreamStatistics::ingestedMembers)
        .sum();
    ingestSamples.save(new IngestSample(server.id(), clock.instant(), ingested));
  }

  private boolean hasStopped(Instance instance) {
    try {
      return !localInstanceRuntime.isRunning(instance.runtimeReference());
    } catch (RuntimeException e) {
      // When in doubt, keep it: the runtime may be temporarily unavailable
      log.log(Level.WARNING, "Could not determine whether " + instance.name() + " runs", e);
      return false;
    }
  }

  private List<Instance> allInstances() {
    return Stream.of(InstanceType.values())
        .flatMap(type -> instanceRepository.findByType(type).stream())
        .toList();
  }
}
