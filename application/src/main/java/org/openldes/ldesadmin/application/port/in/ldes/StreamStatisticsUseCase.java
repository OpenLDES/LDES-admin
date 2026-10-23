package org.openldes.ldesadmin.application.port.in.ldes;

import java.util.List;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;

/**
 * How many members each event stream of an LDES server ingested and paginated.
 *
 * @see EventStreamUseCase for the exceptions thrown
 */
public interface StreamStatisticsUseCase {

  List<StreamStatistics> statistics(InstanceId serverId);
}
