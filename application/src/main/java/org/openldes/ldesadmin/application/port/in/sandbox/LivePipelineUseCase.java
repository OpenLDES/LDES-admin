package org.openldes.ldesadmin.application.port.in.sandbox;

import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.sandbox.LivePipelineRun;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldesadmin.model.sandbox.RunOptions;

public interface LivePipelineUseCase {
  LivePipelineRun start(Pipeline pipeline, RunOptions options);

  LivePipelineRun get(String session);

  void send(String session, RunInput input);

  String connector(String session, String action, String content);

  void stop(String session);
}
