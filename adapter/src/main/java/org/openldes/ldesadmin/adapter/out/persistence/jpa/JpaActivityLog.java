package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import java.util.List;
import org.openldes.ldesadmin.application.port.out.monitoring.ActivityLog;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
public class JpaActivityLog implements ActivityLog {

  /**
   * Longer texts are cut to fit their column.
   */
  private static final int MAX_TITLE = 255;
  private static final int MAX_DETAIL = 2048;

  private final ActivityEventJpaRepository jpaRepository;

  public JpaActivityLog(ActivityEventJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void record(ActivityEvent event) {
    ActivityEventJpaEntity entity = new ActivityEventJpaEntity();
    entity.setId(event.id());
    entity.setOccurredAt(event.time());
    entity.setSeverity(event.severity());
    entity.setSourceType(event.sourceType());
    entity.setSourceId(event.sourceId().value());
    entity.setSourceName(truncate(event.sourceName(), MAX_TITLE));
    entity.setTitle(truncate(event.title(), MAX_TITLE));
    entity.setDetail(truncate(event.detail(), MAX_DETAIL));
    jpaRepository.save(entity);
  }

  @Override
  public List<ActivityEvent> findRecent(int limit) {
    return jpaRepository.findByOrderByOccurredAtDesc(Limit.of(limit)).stream()
        .map(entity -> new ActivityEvent(entity.getId(), entity.getOccurredAt(),
            entity.getSeverity(), entity.getSourceType(), new InstanceId(entity.getSourceId()),
            entity.getSourceName(), entity.getTitle(), entity.getDetail()))
        .toList();
  }

  private static String truncate(String text, int length) {
    return text == null || text.length() <= length ? text : text.substring(0, length - 1) + "…";
  }
}
