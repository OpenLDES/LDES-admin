package org.openldes.ldesadmin.application.port.in.monitoring;

import java.util.List;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;
import org.openldes.ldesadmin.model.monitoring.ThroughputSeries;

/**
 * What the instances did lately.
 */
public interface ActivityUseCase {

  /**
   * Members ingested per hour by the LDES servers over the last 24 hours, as far as LDES-admin
   * sampled them. Servers without samples in that period are left out.
   */
  List<ThroughputSeries> throughput();

  /**
   * The latest activity, newest first.
   */
  List<ActivityEvent> recentActivity(int limit);
}
