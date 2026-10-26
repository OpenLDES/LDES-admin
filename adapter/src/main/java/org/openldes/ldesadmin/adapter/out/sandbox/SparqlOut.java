package org.openldes.ldesadmin.adapter.out.sandbox;

import org.apache.jena.rdf.model.Model;
import org.openldes.ldi.SkolemisationTransformer;
import org.openldes.ldi.exceptions.WriteActionFailedException;
import org.openldes.ldi.factory.DeleteFunctionBuilder;
import org.openldes.ldi.factory.InsertFunctionBuilder;
import org.openldes.ldi.skolemisation.EmptySkolemizer;
import org.openldes.ldi.skolemisation.Skolemizer;
import org.openldes.ldi.skolemisation.SkolemizerImpl;
import org.openldes.ldi.valueobjects.DeleteFunction;
import org.openldes.ldi.valueobjects.InsertFunction;
import org.openldes.ldi.valueobjects.SparqlQuery;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.model.sandbox.OutputResult;

/**
 * The SPARQL output of LDIO ({@code LdioHttpSparqlOut}): replaces what the model describes in a
 * triple store with a SPARQL update, configured as its LDIO configurator does it.
 */
final class SparqlOut implements Output {

  private static final String SPARQL_UPDATE = "application/sparql-update";

  private final LdioConfig config;
  private final String endpoint;
  private final SparqlQuery query;
  private final Skolemizer skolemizer;
  private org.openldes.ldi.HttpSparqlOut output;

  SparqlOut(LdioConfig config) {
    this.config = config;
    this.endpoint = config.property("endpoint");
    InsertFunction insert = config.optional("graph")
        .map(InsertFunctionBuilder::withGraph)
        .orElseGet(InsertFunctionBuilder::create)
        .build();
    this.query = new SparqlQuery(insert, deleteFunction(config));
    this.skolemizer = config.optional("skolemisation.skolemDomain")
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
    if (!execute) {
      return new OutputResult(update, SPARQL_UPDATE,
          "Would POST this update to " + endpoint);
    }
    try {
      output().write(model);
    } catch (WriteActionFailedException e) {
      throw new ComponentException(e.getMessage(), e);
    }
    return new OutputResult(update, SPARQL_UPDATE, "Updated " + endpoint);
  }

  private synchronized org.openldes.ldi.HttpSparqlOut output() {
    if (output == null) {
      output = new org.openldes.ldi.HttpSparqlOut(endpoint, query, skolemizer,
          RequestExecutors.create(config));
    }
    return output;
  }

  private static DeleteFunction deleteFunction(LdioConfig config) {
    if (!config.optionalBoolean("replacement.enabled").orElse(true)) {
      return DeleteFunctionBuilder.disabled();
    }
    return config.optional("replacement.deleteFunction")
        .map(DeleteFunction::ofQuery)
        .orElseGet(() -> config.optional("graph")
            .map(DeleteFunctionBuilder::withGraph)
            .orElseGet(DeleteFunctionBuilder::create)
            .withDepth(config.optionalInteger("replacement.depth").orElse(10)));
  }
}
