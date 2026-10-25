package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;

@Entity
@Table(name = "activity_event")
@Getter
@Setter
@NoArgsConstructor
public class ActivityEventJpaEntity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Severity severity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InstanceType sourceType;

  @Column(nullable = false)
  private UUID sourceId;

  @Column(nullable = false)
  private String sourceName;

  @Column(nullable = false)
  private String title;

  private String detail;
}
