package org.openldes.ldesadmin.model.monitoring;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

/**
 * Something that happened to an instance: an action taken through LDES-admin, or a change
 * LDES-admin noticed (it went down, it came back).
 *
 * @param sourceName name of the instance when it happened; the instance may be gone by now
 * @param detail more information, may be {@code null}
 */
public record ActivityEvent(
    UUID id,
    Instant time,
    Severity severity,
    InstanceType sourceType,
    InstanceId sourceId,
    String sourceName,
    String title,
    String detail) {

  public enum Severity {
    INFO,
    SUCCESS,
    WARN,
    ERROR
  }

  public ActivityEvent {
    Objects.requireNonNull(id, "'id' must not be null");
    Objects.requireNonNull(time, "'time' must not be null");
    Objects.requireNonNull(severity, "'severity' must not be null");
    Objects.requireNonNull(sourceType, "'sourceType' must not be null");
    Objects.requireNonNull(sourceId, "'sourceId' must not be null");
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("'title' must not be blank");
    }
  }

  public static ActivityEvent of(
      Instance source, Severity severity, String title, String detail, Instant now) {
    return new ActivityEvent(
        UUID.randomUUID(), now, severity, source.type(), source.id(), source.name(), title, detail);
  }
}
