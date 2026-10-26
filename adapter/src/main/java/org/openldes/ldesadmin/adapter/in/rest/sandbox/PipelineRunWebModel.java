package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import java.util.List;
import java.util.Locale;
import org.openldes.ldesadmin.model.sandbox.PipelineRun;
import org.openldes.ldesadmin.model.sandbox.StepResult;

/**
 * A pipeline run in the sandbox, step by step. Kinds, statuses and formats are lower case, as the
 * simulation of the frontend has them.
 *
 * @param members the models that reached the outputs, in Turtle
 */
public record PipelineRunWebModel(List<StepWebModel> steps, boolean delivered,
                                  List<String> members) {

  /**
   * @param kind        input, adapter, transformer or output
   * @param status      ok, filtered or error
   * @param format      turtle, json or text: how the outputs are written
   * @param outputs     what the component made: a model each, or what an output sends
   * @param contentType of the outputs, e.g. text/turtle
   */
  public record StepWebModel(String kind, int index, String component, String status,
                             String summary, List<String> outputs, String contentType,
                             String format, long durationMs) {

    static StepWebModel fromDomain(StepResult step) {
      return new StepWebModel(lower(step.kind()), step.index(), step.component(),
          lower(step.status()), step.summary(), step.outputs(), step.contentType(),
          lower(step.format()), step.durationMs());
    }

    private static String lower(Enum<?> value) {
      return value.name().toLowerCase(Locale.ROOT);
    }
  }

  public static PipelineRunWebModel fromDomain(PipelineRun run) {
    return new PipelineRunWebModel(run.steps().stream().map(StepWebModel::fromDomain).toList(),
        run.delivered(), run.members());
  }
}
