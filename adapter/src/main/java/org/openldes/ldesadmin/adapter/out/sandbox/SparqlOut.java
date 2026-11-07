package org.openldes.ldesadmin.adapter.out.sandbox;

import org.apache.jena.rdf.model.Model;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldi.SkolemisationTransformer;
import org.openldes.ldi.factory.DeleteFunctionBuilder;
import org.openldes.ldi.factory.InsertFunctionBuilder;
import org.openldes.ldi.skolemisation.EmptySkolemizer;
import org.openldes.ldi.skolemisation.Skolemizer;
import org.openldes.ldi.skolemisation.SkolemizerImpl;
import org.openldes.ldi.valueobjects.DeleteFunction;
import org.openldes.ldi.valueobjects.InsertFunction;
import org.openldes.ldi.valueobjects.SparqlQuery;

/** Side-effect-free preview of the SPARQL update an output would send. */
final class SparqlOut implements Output {

  private static final String SPARQL_UPDATE = "application/sparql-update";

  private final String endpoint;
  private final SparqlQuery query;
  private final Skolemizer skolemizer;

  SparqlOut(LdioConfig config) {
    this.endpoint = config.property("endpoint");
    InsertFunction insert =
        config
            .optional("graph")
            .map(InsertFunctionBuilder::withGraph)
            .orElseGet(InsertFunctionBuilder::create)
            .build();
    this.query = new SparqlQuery(insert, deleteFunction(config));
    this.skolemizer =
        config
            .optional("skolemisation.skolemDomain")
            .map(SkolemisationTransformer::new)
            .map(transformer -> (Skolemizer) new SkolemizerImpl(transformer))
            .orElseGet(EmptySkolemizer::new);
  }

  @Override
  public OutputResult write(Model model, boolean execute) {
    if (model.isEmpty()) {
      return new OutputResult("", SPARQL_UPDATE, "Sent nothing: the model is empty");
    }
    String update = query.getQueryForModel(skolemizer.skolemize(model));
    return new OutputResult(update, SPARQL_UPDATE, "Would POST this update to " + endpoint);
  }

  private static DeleteFunction deleteFunction(LdioConfig config) {
    if (!config.optionalBoolean("replacement.enabled").orElse(true)) {
      return DeleteFunctionBuilder.disabled();
    }
    return config
        .optional("replacement.deleteFunction")
        .map(DeleteFunction::ofQuery)
        .orElseGet(
            () ->
                config
                    .optional("graph")
                    .map(DeleteFunctionBuilder::withGraph)
                    .orElseGet(DeleteFunctionBuilder::create)
                    .withDepth(config.optionalInteger("replacement.depth").orElse(10)));
  }
}
