package org.openldes.ldesadmin.application.service.monitoring;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import org.openldes.ldesadmin.application.port.out.monitoring.ActivityLog;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;

/**
 * Records what happened to an instance. Recording never makes the action itself fail.
 */
@ApplicationService
public class ActivityRecorder {

  private static final Logger log = System.getLogger(ActivityRecorder.class.getName());

  private final ActivityLog activityLog;
  private final Clock clock;

  public ActivityRecorder(ActivityLog activityLog, Clock clock) {
    this.activityLog = activityLog;
    this.clock = clock;
  }

  public void info(Instance source, String title) {
    record(source, Severity.INFO, title, null);
  }

  public void success(Instance source, String title) {
    record(source, Severity.SUCCESS, title, null);
  }

  public void record(Instance source, Severity severity, String title, String detail) {
    try {
      activityLog.record(ActivityEvent.of(source, severity, title, detail, clock.instant()));
    } catch (RuntimeException e) {
      log.log(Level.WARNING, "Could not record activity '" + title + "'", e);
    }
  }
}
