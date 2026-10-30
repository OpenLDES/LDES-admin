package org.openldes.ldesadmin.application.port.in.ldes;

import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.Fragment;

/**
 * Walks through a view the way an LDES client does.
 *
 * @see EventStreamUseCase for the exceptions thrown
 */
public interface FragmentUseCase {

  /**
   * @param url IRI of the fragment as published by the server, e.g. the node of a relation; {@code
   *     null} for the root of the view
   * @throws IllegalArgumentException if the fragment is not on this server
   */
  Fragment fetch(InstanceId serverId, String collection, String view, String url);
}
