package org.openldes.ldesadmin.application.port.out.sandbox;

import java.util.List;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldesadmin.model.sandbox.RunInput;

/**
 * Runs separate components of LDIO pipelines, configured as in a pipeline definition. Models travel
 * between the components as Turtle documents.
 *
 * <p>Components are kept per session and position, so stateful ones (the change detection filter)
 * remember what they saw across the runs of a session; a changed definition gets a new component.
 *
 * <p>Every method throws a {@link ComponentException} when the component cannot be configured,
 * fails, or takes too long.
 */
public interface ComponentRuntime {

  /**
   * What an input without adapter, such as an LDES client, receives: linked data, read in its
   * content type.
   */
  List<RdfDocument> read(RunInput input);

  /** Turns what the input received into models. */
  List<RdfDocument> adapt(String session, ComponentDefinition adapter, RunInput input);

  /**
   * @param position of the transformer in the pipeline
   * @return the models it passes on: none when it filters the model out, several when it splits it
   */
  List<RdfDocument> transform(
      String session, int position, ComponentDefinition transformer, RdfDocument model);

  /**
   * @param position of the output in the pipeline
   * @param execute whether to really send what it makes; otherwise it only shows that
   */
  OutputResult write(
      String session, int position, ComponentDefinition output, RdfDocument model, boolean execute);
}
