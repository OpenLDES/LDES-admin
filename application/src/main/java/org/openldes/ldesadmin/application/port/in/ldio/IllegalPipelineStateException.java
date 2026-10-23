package org.openldes.ldesadmin.application.port.in.ldio;

import org.openldes.ldesadmin.model.ldio.PipelineStatus;

public class IllegalPipelineStateException extends RuntimeException {

  public IllegalPipelineStateException(String pipeline, PipelineStatus status, String action) {
    super("Cannot %s pipeline %s, because it is %s".formatted(action, pipeline, status));
  }
}
