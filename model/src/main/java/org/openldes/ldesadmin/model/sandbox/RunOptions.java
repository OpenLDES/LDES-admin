package org.openldes.ldesadmin.model.sandbox;

/**
 * How to run a pipeline in the sandbox.
 *
 * @param session        identifies the runs of one pipeline being designed: stateful components,
 *                       such as the change detection filter, remember what they saw across its
 *                       runs, as they do in a running pipeline
 * @param executeOutputs whether outputs really send what they make (HTTP Out posts, SPARQL Out
 *                       updates, …); otherwise a dry run shows what they would send
 */
public record RunOptions(String session, boolean executeOutputs) {

  public RunOptions {
    if (session == null || session.isBlank() || session.length() > 100) {
      throw new IllegalArgumentException("'session' must be 1 to 100 characters");
    }
  }
}
