package org.openldes.ldesadmin.application.port.out.monitoring;

import java.time.Instant;
import java.util.List;
import org.openldes.ldesadmin.model.monitoring.IngestSample;

/**
 * Keeps samples of the number of members the LDES servers ingested, to derive throughput from.
 */
public interface IngestSampleRepository {

  void save(IngestSample sample);

  List<IngestSample> findSince(Instant since);

  void deleteBefore(Instant moment);
}
