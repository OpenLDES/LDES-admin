package org.openldes.ldesadmin.application.port.in.ldes;

import java.util.List;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.BackgroundProcess;

/**
 * The fragmentation and maintenance jobs of an LDES server.
 *
 * @see EventStreamUseCase for the exceptions thrown
 */
public interface BackgroundProcessUseCase {

  List<BackgroundProcess> processes(InstanceId serverId);
}
