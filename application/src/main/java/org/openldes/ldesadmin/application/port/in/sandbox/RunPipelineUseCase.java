package org.openldes.ldesadmin.application.port.in.sandbox;

import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.sandbox.PipelineRun;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldesadmin.model.sandbox.RunOptions;

/**
 * Runs a pipeline in the sandbox, on the LDI components LDIO runs, and shows what every component
 * did: the input as it is received, what the adapter and every transformer made of it, and what the
 * outputs send (or would send, in a dry run).
 *
 * <p>Running a pipeline fetches what its components fetch (JSON-LD contexts, SPARQL {@code SERVICE}
 * calls, the HTTP enricher), and executing its outputs sends what they make wherever they point:
 * the API must be secured wherever LDES-admin reaches what its users should not.
 */
public interface RunPipelineUseCase {

  /**
   * A component that fails makes a step with an error, not an exception.
   *
   * @throws IllegalArgumentException if the pipeline cannot be run, e.g. without an input
   */
  PipelineRun run(Pipeline pipeline, RunInput input, RunOptions options);
}
