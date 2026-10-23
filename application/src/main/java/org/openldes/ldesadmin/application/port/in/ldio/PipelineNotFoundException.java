package org.openldes.ldesadmin.application.port.in.ldio;

public class PipelineNotFoundException extends RuntimeException {

  public PipelineNotFoundException(String pipeline) {
    super("No pipeline found with name " + pipeline);
  }
}
