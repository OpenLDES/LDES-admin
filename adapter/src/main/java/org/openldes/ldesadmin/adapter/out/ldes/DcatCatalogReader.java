package org.openldes.ldesadmin.adapter.out.ldes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Takes the catalog of the server itself out of the DCAT the LDES server combines from its own
 * catalog and those of its event streams and views.
 */
final class DcatCatalogReader {

  private static final String DCAT = "http://www.w3.org/ns/dcat#";

  private static final Resource CATALOG = ResourceFactory.createResource(DCAT + "Catalog");

  /** Properties the server adds, rather than the user. */
  private static final Set<Property> GENERATED =
      Set.of(
          DCTerms.identifier,
          ResourceFactory.createProperty(DCAT, "dataset"),
          ResourceFactory.createProperty(DCAT, "service"),
          ResourceFactory.createProperty(DCAT, "catalog"),
          ResourceFactory.createProperty(DCAT, "record"));

  private DcatCatalogReader() {}

  /**
   * @param base the URL the combined catalog was fetched from
   * @param serverName name of the server, to propose a title when it has no catalog yet
   */
  static CatalogDescription readServerCatalog(
      RdfDocument combined, String base, String serverName) {
    Model model = LdesRdfReader.parse(combined, base);
    List<Resource> catalogs = model.listSubjectsWithProperty(RDF.type, CATALOG).toList();
    if (catalogs.isEmpty()) {
      return new CatalogDescription(null, template(serverName));
    }
    Resource catalog = catalogs.getFirst();
    String catalogId =
        catalog.hasProperty(DCTerms.identifier)
            ? catalog.getProperty(DCTerms.identifier).getString().strip()
            : null;

    // The server expects the catalog as a blank node
    Model own = ModelFactory.createDefaultModel();
    Resource node = own.createResource();
    catalog
        .listProperties()
        .forEach(
            statement -> {
              if (!GENERATED.contains(statement.getPredicate())) {
                own.add(node, statement.getPredicate(), statement.getObject());
                copyBlankNodes(statement.getObject(), own);
              }
            });
    return new CatalogDescription(catalogId, LdesRdfReader.write(own, model.getNsPrefixMap()));
  }

  /** Copies the descriptions of nested blank nodes, e.g. a {@code dct:publisher [ ... ]}. */
  private static void copyBlankNodes(RDFNode start, Model target) {
    Deque<RDFNode> toVisit = new ArrayDeque<>(List.of(start));
    Set<RDFNode> visited = new HashSet<>();
    while (!toVisit.isEmpty()) {
      RDFNode current = toVisit.pop();
      if (current.isAnon() && visited.add(current)) {
        for (Statement statement : current.asResource().listProperties().toList()) {
          target.add(statement);
          toVisit.push(statement.getObject());
        }
      }
    }
  }

  private static RdfDocument template(String serverName) {
    String name = serverName.replace("\\", "\\\\").replace("\"", "\\\"");
    return RdfDocument.turtle(
        """
        @prefix dcat: <http://www.w3.org/ns/dcat#> .
        @prefix dct:  <http://purl.org/dc/terms/> .

        [] a dcat:Catalog ;
            dct:title "%s"@en ;
            dct:description "The event streams published by %s"@en .
        """
            .formatted(name, name));
  }
}
