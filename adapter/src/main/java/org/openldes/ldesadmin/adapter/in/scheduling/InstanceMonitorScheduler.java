package org.openldes.ldesadmin.adapter.in.scheduling;

import org.openldes.ldesadmin.application.port.in.monitoring.MonitorInstancesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lets LDES-admin keep an eye on the instances while nobody is looking: it samples the
 * throughput of the LDES servers and notices instances going down.
 */
@Component
@ConditionalOnBooleanProperty(name = "ldes-admin.monitor.enabled", matchIfMissing = true)
class InstanceMonitorScheduler {

  private static final Logger log = LoggerFactory.getLogger(InstanceMonitorScheduler.class);

  private final MonitorInstancesUseCase monitorInstancesUseCase;

  InstanceMonitorScheduler(MonitorInstancesUseCase monitorInstancesUseCase) {
    this.monitorInstancesUseCase = monitorInstancesUseCase;
  }

  /**
   * Local instances stopped with the previous run of LDES-admin.
   */
  @EventListener(ApplicationReadyEvent.class)
  void removeStoppedLocalInstances() {
    int removed = monitorInstancesUseCase.removeStoppedLocalInstances();
    if (removed > 0) {
      log.info("Removed {} local instance(s) that stopped with a previous run", removed);
    }
  }

  @Scheduled(initialDelayString = "${ldes-admin.monitor.initial-delay:PT10S}",
      fixedDelayString = "${ldes-admin.monitor.interval:PT1M}")
  void monitor() {
    try {
      monitorInstancesUseCase.monitor();
    } catch (RuntimeException e) {
      log.warn("Monitoring the instances failed", e);
    }
  }
}
