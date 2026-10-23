package org.openldes.ldesadmin.model.ldio;

/**
 * Throughput of a pipeline since the LDIO started.
 *
 * @param membersIn  members received by the input of the pipeline
 * @param membersOut members sent by the outputs of the pipeline
 */
public record PipelineStatistics(String pipeline, long membersIn, long membersOut) {

  /**
   * Members that were received, but did not (yet) leave the pipeline.
   */
  public long inFlight() {
    return Math.max(0, membersIn - membersOut);
  }
}
