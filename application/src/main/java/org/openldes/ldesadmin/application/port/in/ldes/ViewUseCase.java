package org.openldes.ldesadmin.application.port.in.ldes;

import java.util.List;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.View;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Manages the views of an event stream.
 *
 * @see EventStreamUseCase for the exceptions thrown
 */
public interface ViewUseCase {

  List<View> list(InstanceId serverId, String collection);

  View get(InstanceId serverId, String collection, String view);

  /**
   * @param definition the {@code tree:viewDescription} of the view
   */
  View create(InstanceId serverId, String collection, RdfDocument definition);

  void delete(InstanceId serverId, String collection, String view);
}
