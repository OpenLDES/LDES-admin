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
import org.openldes.ldesadmin.model.instance.InstanceOrigin;
import org.openldes.ldesadmin.model.instance.InstanceType;

@Entity
@Table(name = "instance")
@Getter
@Setter
@NoArgsConstructor
public class InstanceJpaEntity {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InstanceType type;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String url;

  private String internalUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InstanceOrigin origin;

  private boolean sandbox;

  private String runtimeReference;

  @Column(nullable = false)
  private Instant registeredAt;
}
