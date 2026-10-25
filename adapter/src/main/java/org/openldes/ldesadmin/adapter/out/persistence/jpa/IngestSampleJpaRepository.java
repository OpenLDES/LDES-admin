package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface IngestSampleJpaRepository extends JpaRepository<IngestSampleJpaEntity, Long> {

  List<IngestSampleJpaEntity> findBySampledAtGreaterThanEqualOrderBySampledAtAsc(Instant since);

  @Transactional
  @Modifying
  @Query("delete from IngestSampleJpaEntity sample where sample.sampledAt < :moment")
  int deleteBySampledAtBefore(Instant moment);
}
