package org.openldes.ldesadmin.application.port.out.monitoring;

import java.util.List;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;

/**
 * Keeps the activity of the instances: actions taken through LDES-admin and changes it noticed.
 */
public interface ActivityLog {

  void record(ActivityEvent event);

  /**
   * The latest events, newest first.
   */
  List<ActivityEvent> findRecent(int limit);
}
