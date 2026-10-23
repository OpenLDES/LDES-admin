package org.openldes.ldesadmin.application.port.in.ldes;

import java.util.List;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Manages the event streams of an LDES server.
 *
 * <p>All methods throw an
 * {@link org.openldes.ldesadmin.application.port.in.instance.InstanceNotFoundException} if the
 * server is unknown, and a {@link org.openldes.ldesadmin.application.port.out.RemoteInstanceException}
 * if the server cannot fulfil the request.
 */
public interface EventStreamUseCase {

  List<EventStream> list(InstanceId serverId);

  EventStream get(InstanceId serverId, String collection);

  /**
   * @param definition the {@code ldes:EventStream}, including its shape
   */
  EventStream create(InstanceId serverId, RdfDocument definition);

  void delete(InstanceId serverId, String collection);
}
