package org.openldes.ldesadmin.application.port.in.ldio;

import java.util.List;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;

/**
 * How many members the pipelines of an LDIO processed.
 *
 * @see PipelineUseCase for the exceptions thrown
 */
public interface PipelineStatisticsUseCase {

  List<PipelineStatistics> statistics(InstanceId ldioId);
}
