package org.openldes.ldesadmin.adapter.out.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.model.sandbox.OutputResult;

/**
 * The relational database output of LDIO ({@code LdioRdbOut}): inserts the rows a SPARQL select
 * query finds in the model into a table. It runs dry in the sandbox, which has no database
 * driver: it shows the rows.
 */
final class RdbOut implements Output {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final String table;
  private final Query query;

  RdbOut(LdioConfig config) {
    this.table = config.property("table-name");
    try {
      this.query = QueryFactory.create(config.property("sparql-select-query"));
    } catch (QueryParseException e) {
      throw new ComponentException("The query is not valid SPARQL: " + e.getMessage(), e);
    }
    if (!query.isSelectType()) {
      throw new ComponentException("The query of LdioRdbOut must be a SELECT query");
    }
  }

  @Override
  public OutputResult write(Model model, boolean execute) {
    List<Map<String, String>> rows = new ArrayList<>();
    try (QueryExecution execution = QueryExecutionFactory.create(query, model)) {
      ResultSet results = execution.execSelect();
      List<String> columns = results.getResultVars();
      while (results.hasNext()) {
        QuerySolution solution = results.next();
        Map<String, String> row = new LinkedHashMap<>();
        columns.forEach(column -> row.put(column, valueOf(solution.get(column))));
        rows.add(row);
      }
    }
    String action = "insert %d %s into table %s".formatted(rows.size(),
        rows.size() == 1 ? "row" : "rows", table);
    return new OutputResult(json(rows), "application/json", execute
        ? "LdioRdbOut only runs dry in the sandbox: it would " + action
        : "Would " + action);
  }

  private static String valueOf(RDFNode node) {
    if (node == null) {
      return null;
    }
    return node.isLiteral() ? node.asLiteral().getLexicalForm() : node.toString();
  }

  private static String json(List<Map<String, String>> rows) {
    try {
      return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
