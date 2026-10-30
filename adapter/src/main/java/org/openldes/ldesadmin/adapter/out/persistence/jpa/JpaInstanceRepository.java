package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.springframework.stereotype.Repository;

/**
 * Stores instances in Postgres with JPA. The schema is managed by Flyway ({@code db/migration}).
 */
@Repository
public class JpaInstanceRepository implements InstanceRepository {

  private final InstanceJpaRepository jpaRepository;

  public JpaInstanceRepository(InstanceJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(Instance instance) {
    jpaRepository.save(InstanceMapper.toJpaEntity(instance));
  }

  @Override
  public Optional<Instance> findById(InstanceId id) {
    return jpaRepository.findById(id.value()).map(InstanceMapper::toModel);
  }

  @Override
  public List<Instance> findByType(InstanceType type) {
    return jpaRepository.findByTypeOrderByNameAsc(type).stream()
        .map(InstanceMapper::toModel)
        .toList();
  }

  @Override
  public boolean existsByTypeAndUrl(InstanceType type, URI url) {
    return jpaRepository.existsByTypeAndUrl(type, url.toString());
  }

  @Override
  public void deleteById(InstanceId id) {
    jpaRepository.deleteById(id.value());
  }
}
