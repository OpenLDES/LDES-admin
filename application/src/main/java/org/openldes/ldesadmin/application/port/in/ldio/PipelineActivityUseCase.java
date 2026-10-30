package org.openldes.ldesadmin.application.port.in.ldio;

import java.util.Map;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.LdesClientStatus;

/**
 * Follows what goes through the pipelines of an LDIO, and feeds them.
 *
 * @see PipelineUseCase for the exceptions thrown
 */
public interface PipelineActivityUseCase {

  /** The progress of every pipeline with an LDES client input, by pipeline name. */
  Map<String, LdesClientStatus> ldesClientStatuses(InstanceId ldioId);

  /**
   * Posts a message to a pipeline with an {@code Ldio:HttpIn} input, e.g. JSON for a pipeline with
   * a JSON-LD adapter, or Turtle for one with an RDF adapter.
   *
   * @throws PipelineNotFoundException if no pipeline with that name is deployed
   * @throws IllegalArgumentException if the pipeline does not start with an HTTP input
   */
  void send(InstanceId ldioId, String pipeline, String content, String contentType);
}
