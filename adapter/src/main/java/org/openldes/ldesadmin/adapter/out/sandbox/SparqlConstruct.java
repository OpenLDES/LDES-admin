package org.openldes.ldesadmin.adapter.out.sandbox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.openldes.ldi.sparqlfunctions.DistanceFromStart;
import org.openldes.ldi.sparqlfunctions.FirstCoordinate;
import org.openldes.ldi.sparqlfunctions.LastCoordinate;
import org.openldes.ldi.sparqlfunctions.LineAtIndex;
import org.openldes.ldi.sparqlfunctions.LineLength;
import org.openldes.ldi.sparqlfunctions.MidPoint;
import org.openldes.ldi.sparqlfunctions.PointAtFromStart;

/**
 * The SPARQL construct transformer of LDI ({@code org.openldes.ldi.SparqlConstructTransformer}),
 * with its geospatial functions.
 *
 * <p>LDI's own class registers those functions with a method that Jena 6 no longer has, so this one
 * registers them itself and transforms in the same way: the result of the query is the model, or
 * one model per named graph (with the default graph added).
 */
final class SparqlConstruct {

  static {
    FunctionRegistry registry = FunctionRegistry.get();
    registry.put(FirstCoordinate.NAME, FirstCoordinate.class);
    registry.put(LastCoordinate.NAME, LastCoordinate.class);
    registry.put(LineLength.NAME, LineLength.class);
    registry.put(MidPoint.NAME, MidPoint.class);
    registry.put(PointAtFromStart.NAME, PointAtFromStart.class);
    registry.put(DistanceFromStart.NAME, DistanceFromStart.class);
    registry.put(LineAtIndex.NAME, LineAtIndex.class);
  }

  private final Query query;

  /** The {@code infer} property: the result includes the original model */
  private final boolean includeOriginal;

  SparqlConstruct(Query query, boolean includeOriginal) {
    this.query = query;
    this.includeOriginal = includeOriginal;
  }

  List<Model> transform(Model model) {
    Dataset dataset;
    try (QueryExecution execution =
        QueryExecutionFactory.create(query, DatasetFactory.create(model))) {
      dataset = execution.execConstructDataset();
    }
    List<Model> result =
        dataset.listNames().hasNext()
            ? splitDataset(dataset)
            : new ArrayList<>(List.of(dataset.getDefaultModel()));
    if (includeOriginal && result.size() == 1) {
      result.getFirst().add(model);
    }
    return result;
  }

  private static List<Model> splitDataset(Dataset dataset) {
    List<Model> result = new ArrayList<>();
    Iterator<Resource> names = dataset.listModelNames();
    while (names.hasNext()) {
      Model named = dataset.getNamedModel(names.next());
      named.add(dataset.getDefaultModel());
      result.add(named);
    }
    return result;
  }
}
