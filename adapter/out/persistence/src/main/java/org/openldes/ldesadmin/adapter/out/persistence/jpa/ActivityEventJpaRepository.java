package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityEventJpaRepository extends JpaRepository<ActivityEventJpaEntity, UUID> {

  List<ActivityEventJpaEntity> findByOrderByOccurredAtDesc(Limit limit);
}
