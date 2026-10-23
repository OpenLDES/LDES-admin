package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstanceJpaRepository extends JpaRepository<InstanceJpaEntity, UUID> {

  List<InstanceJpaEntity> findByTypeOrderByNameAsc(InstanceType type);

  boolean existsByTypeAndUrl(InstanceType type, String url);
}
