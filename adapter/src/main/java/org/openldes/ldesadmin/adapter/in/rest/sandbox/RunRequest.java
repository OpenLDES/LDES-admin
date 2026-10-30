package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.openldes.ldesadmin.adapter.in.rest.ldio.PipelineWebModel;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldesadmin.model.sandbox.RunOptions;

/**
 * A pipeline to run in the sandbox, with what its input receives.
 *
 * @param session identifies the runs of one pipeline being designed, see {@link
 *     RunOptions#session()}
 * @param executeOutputs optional, {@code false} by default: a dry run
 */
public record RunRequest(
    @NotNull @Valid PipelineWebModel pipeline,
    @NotNull @Valid InputRequest input,
    @NotBlank String session,
    Boolean executeOutputs) {

  /**
   * @param source optional: the URL it was polled from
   */
  public record InputRequest(
      @NotNull String content, @NotBlank String contentType, String source) {}

  RunInput toInput() {
    return new RunInput(input.content(), input.contentType(), input.source());
  }

  RunOptions toOptions() {
    return new RunOptions(session, Boolean.TRUE.equals(executeOutputs));
  }
}
