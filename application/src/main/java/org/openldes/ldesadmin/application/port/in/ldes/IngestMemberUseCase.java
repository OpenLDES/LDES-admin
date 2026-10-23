package org.openldes.ldesadmin.application.port.in.ldes;

import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Ingests a member directly on an event stream.
 *
 * @see EventStreamUseCase for the exceptions thrown
 */
public interface IngestMemberUseCase {

  void ingest(InstanceId serverId, String collection, RdfDocument member);
}
