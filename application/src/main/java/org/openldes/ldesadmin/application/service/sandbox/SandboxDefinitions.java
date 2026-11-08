package org.openldes.ldesadmin.application.service.sandbox;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.sandbox.Sandbox;

/** What a fresh sandbox starts with. */
final class SandboxDefinitions {

  static final String HTTP_OUT = "Ldio:HttpOut";

  /** Views of the sandbox event stream, by name. */
  static final Map<String, String> VIEWS =
      Map.of(
          "by-page", "tree:fragmentationStrategy () ;",
          "by-time",
              """
          tree:fragmentationStrategy ( [
                  a tree:HierarchicalTimeBasedFragmentation ;
                  tree:maxGranularity "day" ;
                  tree:fragmentationPath prov:generatedAtTime
              ] ) ;""");

  private SandboxDefinitions() {}

  static RdfDocument eventStream(Instance server) {
    return RdfDocument.turtle(
        """
        @prefix ldes:    <https://w3id.org/ldes#> .
        @prefix dcterms: <http://purl.org/dc/terms/> .
        @prefix prov:    <http://www.w3.org/ns/prov#> .
        @prefix tree:    <https://w3id.org/tree#> .
        @prefix sh:      <http://www.w3.org/ns/shacl#> .
        @prefix server:  <%s/> .

        server:%s a ldes:EventStream ;
            ldes:timestampPath prov:generatedAtTime ;
            ldes:versionOfPath dcterms:isVersionOf ;
            tree:shape [ a sh:NodeShape ] .
        """
            .formatted(publishedUrl(server), Sandbox.EVENT_STREAM));
  }

  static RdfDocument view(Instance server, String name) {
    return RdfDocument.turtle(
        """
        @prefix tree:   <https://w3id.org/tree#> .
        @prefix prov:   <http://www.w3.org/ns/prov#> .
        @prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .
        @prefix server: <%s/%s/> .

        server:%s tree:viewDescription [
            %s
            tree:pageSize "10"^^xsd:int
        ] .
        """
            .formatted(publishedUrl(server), Sandbox.EVENT_STREAM, name, VIEWS.get(name)));
  }

  static Pipeline ingestPipeline(URI eventStream) {
    return new Pipeline(
        Sandbox.INGEST_PIPELINE,
        "Receives members over HTTP and ingests them in the "
            + Sandbox.EVENT_STREAM
            + " event stream of the sandbox LDES server",
        new InputDefinition("Ldio:HttpIn", Map.of(), ComponentDefinition.of("Ldio:RdfAdapter")),
        List.of(),
        List.of(new ComponentDefinition(HTTP_OUT, Map.of("endpoint", eventStream.toString()))));
  }

  static boolean feeds(Pipeline pipeline, URI eventStream) {
    return pipeline.outputs().stream()
        .anyMatch(
            output ->
                output.name().equals(HTTP_OUT)
                    && eventStream.toString().equals(output.config().get("endpoint")));
  }

  /** The base of the IRIs the server publishes. */
  private static URI publishedUrl(Instance server) {
    return server.internalUrl() == null ? server.url() : server.internalUrl();
  }
}
