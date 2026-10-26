package org.openldes.ldesadmin.adapter.out.sandbox;

import static java.util.stream.StreamSupport.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.carml.engine.rdf.RdfRmlMapper;
import io.carml.logicalsourceresolver.CsvResolver;
import io.carml.logicalsourceresolver.JsonPathResolver;
import io.carml.logicalsourceresolver.XPathResolver;
import io.carml.model.TriplesMap;
import io.carml.util.RmlMappingLoader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.core.Quad;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelCollector;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.openldes.ldi.functions.ReplaceFunctions;
import org.openldes.ldi.functions.TimeFunctions;
import org.openldes.ldi.functions.WktFunctions;
import org.openldes.ldi.types.LdiAdapter;

/**
 * The RML adapter of LDI ({@code org.openldes.ldi.RmlAdapter}), with two differences, both
 * because LDI runs on Jena 5 and LDES-admin on Jena 6: JSON is handed to carml as a parsed record
 * rather than a stream, and the triples carml makes are converted to Jena here.
 *
 * <p>For a stream, carml finds the records with JsurferJ, whose JSONPath parser was generated with
 * ANTLR 4.7. The ANTLR runtime of Hibernate and Spring Data (4.13) cannot read it. For a record,
 * carml uses Jayway JsonPath, as it does for every expression anyway. Both find the same records
 * for the iterators used in practice ({@code $.*}, {@code $.items[*]}, …).
 */
final class RmlRecordAdapter implements LdiAdapter {

  private static final String RML = "http://semweb.mmlab.be/ns/rml#";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final RdfRmlMapper rmlMapper;
  /** Whether every logical source is JSON, so the input can be handed over as a record */
  private final boolean json;

  RmlRecordAdapter(String mapping) {
    Set<TriplesMap> triplesMaps = RmlMappingLoader.build().load(carmlMapping(mapping));
    this.json = triplesMaps.stream()
        .map(triplesMap -> triplesMap.getLogicalSource().getReferenceFormulation())
        .allMatch(io.carml.vocab.Rdf.Ql.JsonPath::equals);
    this.rmlMapper = RdfRmlMapper.builder()
        .triplesMaps(triplesMaps)
        .setLogicalSourceResolver(io.carml.vocab.Rdf.Ql.JsonPath, JsonPathResolver::getInstance)
        .setLogicalSourceResolver(io.carml.vocab.Rdf.Ql.XPath, XPathResolver::getInstance)
        .setLogicalSourceResolver(io.carml.vocab.Rdf.Ql.Csv, CsvResolver::getInstance)
        .addFunctions(new WktFunctions(), new ReplaceFunctions(), new TimeFunctions())
        .build();
  }

  @Override
  public Stream<Model> apply(Content content) {
    var statements = json
        ? rmlMapper.mapRecord(readJson(content.content()), JsonNode.class)
        : rmlMapper.map(new ByteArrayInputStream(content.content().getBytes(StandardCharsets.UTF_8)));
    Dataset dataset = DatasetFactory.create();
    Objects.requireNonNull(statements.collect(ModelCollector.toModel()).share().block())
        .forEach(statement -> dataset.asDatasetGraph().add(quad(statement)));

    if (!dataset.listNames().hasNext()) {
      return Stream.of(dataset.getDefaultModel());
    }
    return stream(Spliterators.spliteratorUnknownSize(dataset.listNames(), Spliterator.ORDERED),
        false)
        .map(dataset::getNamedModel)
        .map(model -> enrichModel(model, dataset));
  }

  /**
   * carml's own converter (JenaConverters) calls a method Jena 6 no longer has.
   */
  private static Quad quad(org.eclipse.rdf4j.model.Statement statement) {
    Node graph = statement.getContext() == null ? Quad.defaultGraphIRI : node(statement.getContext());
    return Quad.create(graph, node(statement.getSubject()), node(statement.getPredicate()),
        node(statement.getObject()));
  }

  private static Node node(Value value) {
    if (value instanceof IRI iri) {
      return NodeFactory.createURI(iri.stringValue());
    }
    if (value instanceof BNode bnode) {
      return NodeFactory.createBlankNode(bnode.getID());
    }
    Literal literal = (Literal) value;
    return literal.getLanguage()
        .map(language -> NodeFactory.createLiteralLang(literal.getLabel(), language))
        .orElseGet(() -> NodeFactory.createLiteralDT(literal.getLabel(),
            TypeMapper.getInstance().getSafeTypeByName(literal.getDatatype().stringValue())));
  }

  private static JsonNode readJson(String content) {
    try {
      return JSON.readTree(content);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Not valid JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * As LDI does: every logical source reads the input, never a file or URL of the mapping.
   */
  private static org.eclipse.rdf4j.model.Model carmlMapping(String mapping) {
    org.eclipse.rdf4j.model.Model model;
    try {
      model = Rio.parse(new StringReader(mapping), RDFFormat.TURTLE);
    } catch (Exception e) {
      throw new IllegalArgumentException("The mapping is not valid Turtle: " + e.getMessage(), e);
    }
    ValueFactory factory = SimpleValueFactory.getInstance();
    List<Resource> sources = new ArrayList<>();
    List<org.eclipse.rdf4j.model.Statement> added = new ArrayList<>();
    for (var statement : model.getStatements(null, RDF.TYPE,
        factory.createIRI(RML, "LogicalSource"))) {
      Resource source = statement.getSubject();
      sources.add(source);
      var stream = factory.createBNode();
      added.add(factory.createStatement(source, factory.createIRI(RML, "source"), stream));
      added.add(factory.createStatement(stream, RDF.TYPE,
          factory.createIRI("http://carml.taxonic.com/carml/", "Stream")));
    }
    sources.forEach(source -> {
      model.remove(source, RDF.TYPE, factory.createIRI(RML, "LogicalSource"));
      model.remove(source, factory.createIRI(RML, "source"), null);
    });
    model.addAll(added);
    return model;
  }

  /**
   * Adds what the subjects of a named graph refer to in the default graph, as LDI does.
   */
  private static Model enrichModel(Model input, Dataset dataset) {
    Deque<Statement> toProcess = new ArrayDeque<>();
    List<Statement> added = new ArrayList<>();
    input.listStatements().forEach(toProcess::push);
    while (!toProcess.isEmpty()) {
      Statement statement = toProcess.pop();
      if (statement.getObject().isResource()) {
        dataset.getDefaultModel()
            .listStatements(statement.getResource(), null, (RDFNode) null)
            .forEach(referred -> {
              added.add(referred);
              if (!referred.getObject().isLiteral()) {
                toProcess.add(referred);
              }
            });
      }
    }
    input.add(added);
    return input;
  }
}
