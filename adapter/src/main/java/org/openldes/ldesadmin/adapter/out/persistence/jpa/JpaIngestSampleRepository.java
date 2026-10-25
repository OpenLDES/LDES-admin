package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import java.time.Instant;
import java.util.List;
import org.openldes.ldesadmin.application.port.out.monitoring.IngestSampleRepository;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.monitoring.IngestSample;
import org.springframework.stereotype.Repository;

@Repository
public class JpaIngestSampleRepository implements IngestSampleRepository {

  private final IngestSampleJpaRepository jpaRepository;

  public JpaIngestSampleRepository(IngestSampleJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(IngestSample sample) {
    IngestSampleJpaEntity entity = new IngestSampleJpaEntity();
    entity.setInstanceId(sample.instanceId().value());
    entity.setSampledAt(sample.time());
    entity.setIngestedMembers(sample.ingestedMembers());
    jpaRepository.save(entity);
  }

  @Override
  public List<IngestSample> findSince(Instant since) {
    return jpaRepository.findBySampledAtGreaterThanEqualOrderBySampledAtAsc(since).stream()
        .map(entity -> new IngestSample(new InstanceId(entity.getInstanceId()),
            entity.getSampledAt(), entity.getIngestedMembers()))
        .toList();
  }

  @Override
  public void deleteBefore(Instant moment) {
    jpaRepository.deleteBySampledAtBefore(moment);
  }
}
