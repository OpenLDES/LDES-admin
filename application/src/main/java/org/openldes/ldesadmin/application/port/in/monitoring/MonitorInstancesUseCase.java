package org.openldes.ldesadmin.application.port.in.monitoring;

/**
 * Keeps an eye on the instances: samples how many members the LDES servers ingested and records
 * when an instance goes down or comes back.
 */
public interface MonitorInstancesUseCase {

  void monitor();

  /**
   * Removes local instances whose container no longer runs, typically because they belonged to a
   * previous run of LDES-admin.
   *
   * @return the number of removed instances
   */
  int removeStoppedLocalInstances();
}
