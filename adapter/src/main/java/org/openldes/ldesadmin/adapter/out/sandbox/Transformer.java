package org.openldes.ldesadmin.adapter.out.sandbox;

import java.util.List;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldi.ChangeDetectionFilter;
import org.openldes.ldi.GeoJsonToWktTransformer;
import org.openldes.ldi.SkolemisationTransformer;
import org.openldes.ldi.VersionMaterialiser;
import org.openldes.ldi.VersionObjectCreator;
import org.openldes.ldi.extractor.EmptyPropertyExtractor;
import org.openldes.ldi.extractor.PropertyExtractor;
import org.openldes.ldi.extractor.PropertyPathExtractor;
import org.openldes.ldi.repositories.inmemory.InMemoryHashedStateMemberRepository;

/**
 * The transformers of LDIO, configured as their LDIO configurators do it. A transformer passes on
 * the models it makes of a model: none when it filters it out.
 */
@FunctionalInterface
interface Transformer {

  String PROV_GENERATED_AT_TIME = "http://www.w3.org/ns/prov#generatedAtTime";
  String DCTERMS_IS_VERSION_OF = "http://purl.org/dc/terms/isVersionOf";

  List<Model> transform(Model model);

  static Transformer create(String name, LdioConfig config) {
    return switch (name) {
      case "Ldio:SparqlConstructTransformer" ->
          new SparqlConstruct(query(config), config.optionalBoolean("infer").orElse(false))
              ::transform;
      case "Ldio:VersionObjectCreator" -> {
        VersionObjectCreator creator = versionObjectCreator(config);
        yield model -> List.of(creator.transform(model));
      }
      case "Ldio:VersionMaterialiser" -> {
        var materialiser =
            new VersionMaterialiser(
                ModelFactory.createDefaultModel()
                    .createProperty(
                        config.optional("versionOf-property").orElse(DCTERMS_IS_VERSION_OF)),
                config.optionalBoolean("restrict-to-members").orElse(false));
        yield model -> List.of(materialiser.transform(model));
      }
      case "Ldio:GeoJsonToWktTransformer" -> {
        var transformer =
            new GeoJsonToWktTransformer(
                config.optionalBoolean("transform-to-rdf+wkt-enabled").orElse(false));
        yield model -> List.of(transformer.transform(model));
      }
      case "Ldio:SkolemisationTransformer" -> {
        var transformer = new SkolemisationTransformer(config.property("skolem-domain"));
        yield model -> List.of(transformer.transform(model));
      }
      case "Ldio:ChangeDetectionFilter" -> {
        // In memory, whatever state the pipeline keeps it in: it lives as long as the session
        var filter = new ChangeDetectionFilter(new InMemoryHashedStateMemberRepository());
        yield model -> {
          Model passed = filter.transform(model);
          return passed.isEmpty() ? List.of() : List.of(passed);
        };
      }
      case "Ldio:HttpEnricher" -> new HttpEnricher(config)::transform;
      default -> throw new ComponentException(name + " is not a transformer the sandbox can run");
    };
  }

  private static org.apache.jena.query.Query query(LdioConfig config) {
    try {
      return QueryFactory.create(config.property("query"));
    } catch (QueryParseException e) {
      throw new ComponentException("The query is not valid SPARQL: " + e.getMessage(), e);
    }
  }

  private static VersionObjectCreator versionObjectCreator(LdioConfig config) {
    Model model = ModelFactory.createDefaultModel();
    List<String> memberTypes = config.list("member-type");
    if (memberTypes.stream().allMatch(String::isBlank)) {
      throw new ComponentException(config.component() + " needs 'member-type'");
    }
    PropertyExtractor dateObserved =
        config
            .optional("date-observed-property")
            .map(PropertyPathExtractor::from)
            .map(PropertyExtractor.class::cast)
            .orElseGet(EmptyPropertyExtractor::new);
    List<Resource> types = memberTypes.stream().map(model::createResource).toList();
    return new VersionObjectCreator(
        dateObserved,
        types,
        config.optional("delimiter").orElse("/"),
        model.createProperty(
            config.optional("generatedAt-property").orElse(PROV_GENERATED_AT_TIME)),
        model.createProperty(config.optional("versionOf-property").orElse(DCTERMS_IS_VERSION_OF)));
  }
}
