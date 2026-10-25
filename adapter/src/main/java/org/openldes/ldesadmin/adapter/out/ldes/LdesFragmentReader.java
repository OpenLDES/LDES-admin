package org.openldes.ldesadmin.adapter.out.ldes;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.openldes.ldesadmin.model.ldes.Fragment;
import org.openldes.ldesadmin.model.ldes.Fragment.Member;
import org.openldes.ldesadmin.model.ldes.Fragment.Relation;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Reads a page of a view, as served by the fetch API of an LDES server.
 */
final class LdesFragmentReader {

  private static final String TREE = "https://w3id.org/tree#";

  private static final Resource NODE = ResourceFactory.createResource(TREE + "Node");
  private static final Resource PLAIN_RELATION = ResourceFactory.createResource(
      TREE + "Relation");
  private static final Property RELATION = ResourceFactory.createProperty(TREE, "relation");
  private static final Property TARGET = ResourceFactory.createProperty(TREE, "node");
  private static final Property PATH = ResourceFactory.createProperty(TREE, "path");
  private static final Property VALUE = ResourceFactory.createProperty(TREE, "value");
  private static final Property REMAINING_ITEMS = ResourceFactory.createProperty(TREE,
      "remainingItems");
  private static final Property MEMBER = ResourceFactory.createProperty(TREE, "member");

  private LdesFragmentReader() {
  }

  /**
   * @param base          the URL the page was fetched from, to resolve relative IRIs against
   * @param timestampPath property holding the timestamp of a member, may be {@code null}
   * @param versionOfPath property linking a member to its state object, may be {@code null}
   */
  static Fragment read(RdfDocument document, String base, boolean immutable,
      String timestampPath, String versionOfPath) {
    Model model = LdesRdfReader.parse(document, base);
    Optional<Resource> page = page(model, base);
    List<Relation> relations = page.stream()
        .flatMap(node -> node.listProperties(RELATION).toList().stream())
        .map(Statement::getObject)
        .filter(RDFNode::isResource)
        .map(relation -> toRelation(relation.asResource()))
        .filter(Objects::nonNull)
        .toList();

    List<Resource> memberNodes = model.listObjectsOfProperty(MEMBER).toList().stream()
        .filter(RDFNode::isResource)
        .map(RDFNode::asResource)
        .toList();
    // The content of a member stops at the structure of the page and at other members
    Set<Resource> boundaries = new HashSet<>(memberNodes);
    boundaries.addAll(model.listSubjectsWithProperty(MEMBER).toList());
    page.ifPresent(boundaries::add);
    List<Member> members = memberNodes.stream()
        .map(member -> toMember(member, timestampPath, versionOfPath, boundaries))
        .sorted(comparing(Member::timestamp, nullsLast(naturalOrder()))
            .thenComparing(Member::id))
        .toList();

    String url = page.filter(Resource::isURIResource).map(Resource::getURI).orElse(base);
    return new Fragment(url, immutable, relations, members);
  }

  /**
   * The node describing the page itself; the nodes it links to are not typed.
   */
  private static Optional<Resource> page(Model model, String base) {
    List<Resource> typed = model.listSubjectsWithProperty(RDF.type, NODE).toList();
    List<Resource> nodes = typed.isEmpty()
        ? model.listSubjectsWithProperty(RELATION).toList()
        : typed;
    return nodes.stream()
        .filter(node -> node.isURIResource() && node.getURI().equals(base))
        .findFirst()
        .or(() -> nodes.stream().findFirst());
  }

  private static Relation toRelation(Resource relation) {
    Resource target = relation.getPropertyResourceValue(TARGET);
    if (target == null || !target.isURIResource()) {
      return null;
    }
    String type = relation.listProperties(RDF.type).toList().stream()
        .map(Statement::getResource)
        .filter(candidate -> !candidate.equals(PLAIN_RELATION))
        .map(Resource::getLocalName)
        .findFirst()
        .orElse("Relation");
    Long remainingItems = Optional.ofNullable(relation.getProperty(REMAINING_ITEMS))
        .map(statement -> statement.getLiteral().getLong())
        .orElse(null);
    return new Relation(type, valueOf(relation, PATH), valueOf(relation, VALUE), target.getURI(),
        remainingItems);
  }

  private static Member toMember(Resource member, String timestampPath, String versionOfPath,
      Set<Resource> boundaries) {
    String id = member.isURIResource() ? member.getURI() : "_:" + member.getId();
    return new Member(id, valueOf(member, versionOfPath), valueOf(member, timestampPath),
        LdesRdfReader.describe(member, boundaries));
  }

  private static String valueOf(Resource subject, String property) {
    return property == null ? null
        : valueOf(subject, ResourceFactory.createProperty(property));
  }

  private static String valueOf(Resource subject, Property property) {
    Statement statement = subject.getProperty(property);
    if (statement == null) {
      return null;
    }
    RDFNode object = statement.getObject();
    if (object.isLiteral()) {
      return object.asLiteral().getLexicalForm();
    }
    return object.isURIResource() ? object.asResource().getURI() : object.toString();
  }
}
