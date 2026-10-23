package org.openldes.ldesadmin.adapter.out.ldes;

import static java.util.Comparator.comparing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.RDF;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.Fragmentation;
import org.openldes.ldesadmin.model.ldes.RetentionPolicy;
import org.openldes.ldesadmin.model.ldes.View;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Reads the event streams and views returned by the LDES server admin API.
 */
final class LdesRdfReader {

  private static final String LDES = "https://w3id.org/ldes#";
  private static final String TREE = "https://w3id.org/tree#";

  private static final Resource EVENT_STREAM = ResourceFactory.createResource(LDES + "EventStream");
  private static final Property TIMESTAMP_PATH = ResourceFactory.createProperty(LDES,
      "timestampPath");
  private static final Property VERSION_OF_PATH = ResourceFactory.createProperty(LDES,
      "versionOfPath");
  private static final Property CREATE_VERSIONS = ResourceFactory.createProperty(LDES,
      "createVersions");
  private static final Property RETENTION_POLICY = ResourceFactory.createProperty(LDES,
      "retentionPolicy");
  private static final Property VIEW = ResourceFactory.createProperty(TREE, "view");
  private static final Property VIEW_DESCRIPTION = ResourceFactory.createProperty(TREE,
      "viewDescription");
  private static final Property PAGE_SIZE = ResourceFactory.createProperty(TREE, "pageSize");
  private static final Property FRAGMENTATION_STRATEGY = ResourceFactory.createProperty(TREE,
      "fragmentationStrategy");

  private LdesRdfReader() {
  }

  /**
   * @param base IRI to resolve relative IRIs against; servers configured with
   *             {@code use-relative-url} return relative IRIs
   */
  static List<EventStream> readEventStreams(RdfDocument document, String base) {
    Model model = parse(document, base);
    return model.listSubjectsWithProperty(RDF.type, EVENT_STREAM).toList().stream()
        .map(LdesRdfReader::toEventStream)
        .sorted(comparing(EventStream::collection))
        .toList();
  }

  static List<View> readViews(String collection, RdfDocument document, String base) {
    Model model = parse(document, base);
    return model.listSubjectsWithProperty(VIEW_DESCRIPTION).toList().stream()
        .map(view -> toView(collection, view))
        .sorted(comparing(View::name))
        .toList();
  }

  private static EventStream toEventStream(Resource stream) {
    List<String> views = stream.listProperties(VIEW).toList().stream()
        .map(statement -> lastSegment(statement.getResource()))
        .sorted()
        .toList();
    boolean createVersions = Optional.ofNullable(stream.getProperty(CREATE_VERSIONS))
        .map(statement -> statement.getLiteral().getBoolean())
        .orElse(false);
    return new EventStream(lastSegment(stream), uriOf(stream, TIMESTAMP_PATH),
        uriOf(stream, VERSION_OF_PATH), createVersions, views, describe(stream));
  }

  private static View toView(String collection, Resource view) {
    Resource description = view.getPropertyResourceValue(VIEW_DESCRIPTION);
    int pageSize = Optional.ofNullable(description.getProperty(PAGE_SIZE))
        .map(statement -> statement.getLiteral().getInt())
        .orElse(0);
    List<Fragmentation> fragmentations = fragmentationNodes(description).stream()
        .map(node -> new Fragmentation(typeOf(node), propertiesOf(node)))
        .toList();
    List<RetentionPolicy> retentionPolicies = description.listProperties(RETENTION_POLICY)
        .toList().stream()
        .map(statement -> statement.getResource())
        .map(node -> new RetentionPolicy(typeOf(node), propertiesOf(node)))
        .toList();
    return new View(collection, lastSegment(view), pageSize, fragmentations, retentionPolicies,
        describe(view));
  }

  private static List<Resource> fragmentationNodes(Resource description) {
    Resource strategy = description.getPropertyResourceValue(FRAGMENTATION_STRATEGY);
    if (strategy == null || !strategy.canAs(RDFList.class)) {
      return List.of();
    }
    return strategy.as(RDFList.class).asJavaList().stream()
        .filter(RDFNode::isResource)
        .map(RDFNode::asResource)
        .toList();
  }

  private static String typeOf(Resource node) {
    Resource type = node.getPropertyResourceValue(RDF.type);
    return type == null ? "Unknown" : type.getLocalName();
  }

  private static Map<String, String> propertiesOf(Resource node) {
    Map<String, String> properties = new LinkedHashMap<>();
    node.listProperties().forEach(statement -> {
      if (!statement.getPredicate().equals(RDF.type)) {
        properties.put(statement.getPredicate().getLocalName(), valueOf(statement.getObject()));
      }
    });
    return properties;
  }

  private static String valueOf(RDFNode node) {
    if (node.isLiteral()) {
      Literal literal = node.asLiteral();
      return literal.getLexicalForm();
    }
    return node.isURIResource() ? node.asResource().getURI() : node.toString();
  }

  private static String uriOf(Resource subject, Property property) {
    Resource value = subject.getPropertyResourceValue(property);
    return value == null ? null : value.getURI();
  }

  /**
   * The last path segment of the IRI of an event stream or view is its name.
   */
  static String lastSegment(Resource resource) {
    if (!resource.isURIResource()) {
      throw new IllegalArgumentException("Expected a named resource, but got " + resource);
    }
    String uri = resource.getURI().replaceAll("[/#]+$", "");
    return uri.substring(Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('#')) + 1);
  }

  /**
   * Everything that can be reached from a subject, serialised as Turtle.
   */
  private static RdfDocument describe(Resource subject) {
    Model description = ModelFactory.createDefaultModel();
    description.setNsPrefixes(subject.getModel().getNsPrefixMap());
    Deque<Resource> toVisit = new ArrayDeque<>(List.of(subject));
    Set<Resource> visited = new HashSet<>();
    while (!toVisit.isEmpty()) {
      Resource current = toVisit.pop();
      if (visited.add(current)) {
        current.listProperties().forEach(statement -> {
          description.add(statement);
          if (statement.getObject().isResource()) {
            toVisit.push(statement.getResource());
          }
        });
      }
    }
    return RdfDocument.turtle(RDFWriter.source(description).lang(Lang.TURTLE).asString());
  }

  private static Model parse(RdfDocument document, String base) {
    try {
      return RDFParser.fromString(document.content(), lang(document)).base(base).toModel();
    } catch (RiotException e) {
      throw new IllegalArgumentException("Invalid RDF: " + e.getMessage(), e);
    }
  }

  private static Lang lang(RdfDocument document) {
    return switch (document.format()) {
      case TURTLE -> Lang.TURTLE;
      case JSON_LD -> Lang.JSONLD;
      case N_QUADS -> Lang.NQUADS;
      case N_TRIPLES -> Lang.NTRIPLES;
    };
  }
}
