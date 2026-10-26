package org.openldes.ldesadmin.model.sandbox;

import java.util.List;

/**
 * A pipeline run in the sandbox: what every component did, step by step.
 *
 * @param delivered whether models reached the outputs
 * @param members   the models that reached the outputs, in Turtle
 */
public record PipelineRun(List<StepResult> steps, boolean delivered, List<String> members) {

  public PipelineRun {
    steps = steps == null ? List.of() : List.copyOf(steps);
    members = members == null ? List.of() : List.copyOf(members);
  }
}
