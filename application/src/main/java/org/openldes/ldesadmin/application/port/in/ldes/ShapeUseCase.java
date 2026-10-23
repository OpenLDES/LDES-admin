package org.openldes.ldesadmin.application.port.in.ldes;

import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Manages the SHACL shape of an event stream.
 *
 * @see EventStreamUseCase for the exceptions thrown
 */
public interface ShapeUseCase {

  RdfDocument get(InstanceId serverId, String collection);

  RdfDocument update(InstanceId serverId, String collection, RdfDocument shape);
}
