package org.openldes.ldesadmin.adapter.out.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldesadmin.model.sandbox.RunInput;

/** Runs the real LDI components, on the Jena of LDES-admin. */
class LdiComponentRuntimeTest {

  private static final String SESSION = "session-1";
  private static final RdfDocument PARCEL =
      RdfDocument.turtle(
          """
      @prefix ex: <http://example.org/> .
      @prefix dcterms: <http://purl.org/dc/terms/> .
      @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
      ex:parcel1 a ex:Parcel ; ex:area "512.4"^^xsd:double ;
        dcterms:modified "2026-10-26T10:00:00Z"^^xsd:dateTime ;
        ex:owner [ ex:name "Jane" ] .
      """);
  private static final String RML_MAPPING =
      """
      @prefix rml: <http://semweb.mmlab.be/ns/rml#> .
      @prefix rr: <http://www.w3.org/ns/r2rml#> .
      @prefix ql: <http://semweb.mmlab.be/ns/ql#> .
      @prefix ex: <http://example.org/> .
      ex:Map a rr:TriplesMap ;
        rml:logicalSource [ a rml:LogicalSource ; rml:source "/etc/hosts" ;
          rml:referenceFormulation ql:JSONPath ; rml:iterator "$.*" ] ;
        rr:subjectMap [ rr:template "http://example.org/{id}" ;
          rr:graphMap [ rr:template "http://example.org/{id}" ] ; rr:class ex:CarPark ] ;
        rr:predicateObjectMap [ rr:predicate ex:name ; rr:objectMap [ rml:reference "name" ] ] .
      """;

  private final LdiComponentRuntime runtime =
      new LdiComponentRuntime(Duration.ofSeconds(10), Clock.systemUTC());
  private HttpServer server;

  @AfterEach
  void tearDown() {
    runtime.destroy();
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void readsWhatAnInputWithoutAdapterReceivesInItsRdfFormat() {
    List<RdfDocument> models =
        runtime.read(
            RunInput.of(
                "<http://example.org/a> <http://example.org/b> \"c\" .", "application/n-triples"));

    assertThat(models)
        .singleElement()
        .satisfies(model -> assertThat(model.content()).contains("<http://example.org/a>"));
    assertThatThrownBy(() -> runtime.read(RunInput.of("a,b", "text/csv")))
        .isInstanceOf(ComponentException.class)
        .hasMessage("text/csv is not an RDF format");
  }

  @Test
  void theJsonToLdAdapterMakesAModelPerObject() {
    var adapter =
        new ComponentDefinition(
            "Ldio:JsonToLdAdapter",
            Map.of("context", "{\"@context\": {\"@vocab\": \"http://example.org/\"}}"));

    List<RdfDocument> models =
        runtime.adapt(
            SESSION,
            adapter,
            RunInput.of(
                "[{\"@id\": \"http://example.org/a\", \"area\": 82}, {\"@id\": \"http://example.org/b\"}]",
                "application/json"));

    assertThat(models).hasSize(2);
    assertThat(models.getFirst().content()).contains(":a", ":area", "82");
  }

  @Test
  void theRmlAdapterMakesAModelPerNamedGraphAndOnlyReadsTheInput() {
    var adapter = new ComponentDefinition("Ldio:RmlAdapter", Map.of("mapping", RML_MAPPING));

    List<RdfDocument> models =
        runtime.adapt(
            SESSION,
            adapter,
            RunInput.of(
                "[{\"id\": \"vrijdagmarkt\", \"name\": \"Vrijdagmarkt\"}, {\"id\": \"reep\", \"name\": \"Reep\"}]",
                "application/json"));

    assertThat(models).hasSize(2);
    assertThat(models)
        .anySatisfy(
            model -> assertThat(model.content()).contains("vrijdagmarkt", "\"Vrijdagmarkt\""));
  }

  @Test
  void theSparqlConstructTransformerRunsTheQueryWithTheFunctionsOfLdi() {
    var sparql =
        new ComponentDefinition(
            "Ldio:SparqlConstructTransformer",
            Map.of(
                "query",
                """
        PREFIX ex: <http://example.org/>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>
        PREFIX ldi: <https://opengis.net/def/function/geosparql/custom#>
        CONSTRUCT { ?s ex:size ?area ; ex:start ?start }
        WHERE {
          ?s ex:area ?area .
          BIND(ldi:firstCoordinate("LINESTRING(4 50, 5 51)"^^geo:wktLiteral) AS ?start)
        }
        """));

    List<RdfDocument> models = runtime.transform(SESSION, 0, sparql, PARCEL);

    assertThat(models)
        .singleElement()
        .satisfies(
            model ->
                assertThat(model.content())
                    .contains("ex:size", "512.4", "POINT(4 50)")
                    .doesNotContain("Jane"));
  }

  @Test
  void theSparqlConstructTransformerMakesAModelPerNamedGraph() {
    var sparql =
        new ComponentDefinition(
            "Ldio:SparqlConstructTransformer",
            Map.of(
                "query",
                "CONSTRUCT { GRAPH ?s { ?s ?p ?o } } WHERE { ?s ?p ?o FILTER(isIRI(?s)) }"));

    assertThat(runtime.transform(SESSION, 0, sparql, PARCEL)).hasSize(1);
  }

  @Test
  void explainsAQueryThatIsNotSparqlInsteadOfReadingAFileOfThatName() {
    var sparql =
        new ComponentDefinition("Ldio:SparqlConstructTransformer", Map.of("query", "/etc/hosts"));

    assertThatThrownBy(() -> runtime.transform(SESSION, 0, sparql, PARCEL))
        .isInstanceOf(ComponentException.class)
        .hasMessageStartingWith("The query is not valid SPARQL");
  }

  @Test
  void theVersionObjectCreatorMakesAVersion() {
    var creator =
        new ComponentDefinition(
            "Ldio:VersionObjectCreator",
            Map.of(
                "member-type", "http://example.org/Parcel",
                "date-observed-property", "<http://purl.org/dc/terms/modified>"));

    assertThat(runtime.transform(SESSION, 0, creator, PARCEL))
        .singleElement()
        .satisfies(
            model ->
                assertThat(model.content())
                    .contains("<http://example.org/parcel1/2026-10-26T10:00:00Z>")
                    .contains("isVersionOf"));
  }

  @Test
  void theChangeDetectionFilterRemembersWhatItSawInTheSession() {
    var filter = ComponentDefinition.of("Ldio:ChangeDetectionFilter");

    assertThat(runtime.transform(SESSION, 1, filter, PARCEL)).hasSize(1);
    assertThat(runtime.transform(SESSION, 1, filter, PARCEL)).isEmpty();
    assertThat(runtime.transform("session-2", 1, filter, PARCEL)).hasSize(1);

    // A changed definition is a new filter, as after redeploying the pipeline
    var redeployed =
        new ComponentDefinition("Ldio:ChangeDetectionFilter", Map.of("state", "memory"));
    assertThat(runtime.transform(SESSION, 1, redeployed, PARCEL)).hasSize(1);
  }

  @Test
  void matchesKeysLikeLdio() {
    var skolemiser =
        new ComponentDefinition(
            "Ldio:SkolemisationTransformer", Map.of("skolemDomain", "https://example.org"));

    assertThat(runtime.transform(SESSION, 0, skolemiser, PARCEL))
        .singleElement()
        .satisfies(
            model ->
                assertThat(model.content())
                    .contains("https://example.org/.well-known/genid/")
                    .doesNotContain("[ "));
  }

  @Test
  void consoleOutWritesWhatItsWriterWritesWithTheFrame() {
    var console =
        new ComponentDefinition(
            "Ldio:ConsoleOut",
            Map.of(
                "rdf-writer",
                Map.of(
                    "content-type", "application/ld+json",
                    "frame",
                        "{\"@context\": {\"@vocab\": \"http://example.org/\"}, \"@type\": \"Parcel\"}")));

    OutputResult result = runtime.write(SESSION, 0, console, PARCEL, false);

    assertThat(result.contentType()).isEqualTo("application/ld+json");
    assertThat(result.serialisation()).contains("\"@type\":\"Parcel\"", "\"area\"");
    assertThat(result.summary()).isEqualTo("Printed to the console of the LDIO");
  }

  @Test
  void httpOutShowsWhatItWouldPostInADryRun() {
    var httpOut =
        new ComponentDefinition("Ldio:HttpOut", Map.of("endpoint", "http://localhost:1/parcels"));

    OutputResult result = runtime.write(SESSION, 0, httpOut, PARCEL, false);

    assertThat(result.summary()).isEqualTo("Would POST text/turtle to http://localhost:1/parcels");
    assertThat(result.serialisation()).contains("ex:parcel1");
  }

  @Test
  void httpOutPostsWhenExecuted() throws IOException {
    AtomicReference<String> received = new AtomicReference<>();
    String endpoint = serve(202, received);
    var httpOut =
        new ComponentDefinition(
            "Ldio:HttpOut",
            Map.of(
                "endpoint", endpoint, "rdf-writer", Map.of("content-type", "application/n-quads")));

    OutputResult result = runtime.write(SESSION, 0, httpOut, PARCEL, true);

    assertThat(result.summary()).isEqualTo("POST %s: 202".formatted(endpoint));
    assertThat(received.get())
        .startsWith("application/n-quads ")
        .contains("<http://example.org/parcel1> <http://example.org/area> ");
  }

  @Test
  void reportsWhatTheEndpointOfAnHttpOutAnswers() throws IOException {
    String endpoint = serve(400, new AtomicReference<>());
    var httpOut =
        new ComponentDefinition(
            "Ldio:HttpOut", Map.of("endpoint", endpoint, "retries", Map.of("enabled", false)));

    assertThatThrownBy(() -> runtime.write(SESSION, 0, httpOut, PARCEL, true))
        .isInstanceOf(ComponentException.class)
        .hasMessage("POST %s answered 400 - Not a member".formatted(endpoint));
  }

  @Test
  void httpSparqlOutShowsTheUpdateItWouldSend() {
    var sparqlOut =
        new ComponentDefinition(
            "Ldio:HttpSparqlOut",
            Map.of("endpoint", "http://localhost:1/sparql", "graph", "http://example.org/graph"));

    OutputResult result = runtime.write(SESSION, 0, sparqlOut, PARCEL, false);

    assertThat(result.contentType()).isEqualTo("application/sparql-update");
    assertThat(result.serialisation())
        .contains(
            "DELETE FROM <http://example.org/graph>",
            "INSERT DATA",
            "<http://example.org/parcel1>");
  }

  @Test
  void outputsWithoutAClientInLdesAdminRunDry() {
    var kafkaOut =
        new ComponentDefinition(
            "Ldio:KafkaOut", Map.of("bootstrap-servers", "localhost:9092", "topic", "parcels"));
    var rdbOut =
        new ComponentDefinition(
            "Ldio:LdioRdbOut",
            Map.of(
                "table-name",
                "parcels",
                "sparql-select-query",
                "SELECT ?s ?area WHERE { ?s <http://example.org/area> ?area }"));

    assertThat(runtime.write(SESSION, 0, kafkaOut, PARCEL, true).summary())
        .isEqualTo("Kafka Out only runs dry in the sandbox: it would send it to topic parcels");
    OutputResult rows = runtime.write(SESSION, 1, rdbOut, PARCEL, false);
    assertThat(rows.summary()).isEqualTo("Would insert 1 row into table parcels");
    assertThat(rows.serialisation()).contains("\"area\" : \"512.4\"");
  }

  @Test
  void explainsWhatIsMissingOrUnknown() {
    assertThatThrownBy(
            () -> runtime.write(SESSION, 0, ComponentDefinition.of("Ldio:HttpOut"), PARCEL, false))
        .isInstanceOf(ComponentException.class)
        .hasMessage("Ldio:HttpOut needs 'endpoint'");
    assertThatThrownBy(
            () -> runtime.transform(SESSION, 0, ComponentDefinition.of("Ldio:Telepathy"), PARCEL))
        .isInstanceOf(ComponentException.class)
        .hasMessage("Ldio:Telepathy is not a transformer the sandbox can run");
  }

  @Test
  void stopsAComponentThatTakesTooLong() {
    var impatient = new LdiComponentRuntime(Duration.ofMillis(1), Clock.systemUTC());
    var adapter = new ComponentDefinition("Ldio:RmlAdapter", Map.of("mapping", RML_MAPPING));
    try {
      assertThatThrownBy(
              () ->
                  impatient.adapt(
                      SESSION,
                      adapter,
                      RunInput.of("[{\"id\": \"a\", \"name\": \"A\"}]", "application/json")))
          .isInstanceOf(ComponentException.class)
          .hasMessage("Took longer than 1 ms, the limit of the sandbox");
    } finally {
      impatient.destroy();
    }
  }

  /**
   * An endpoint that answers every POST with the given status, and keeps the content type and body
   * it received.
   */
  private String serve(int status, AtomicReference<String> received) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          received.set(
              exchange.getRequestHeaders().getFirst("Content-Type")
                  + " "
                  + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body =
              status >= 400 ? "Not a member".getBytes(StandardCharsets.UTF_8) : new byte[0];
          exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return "http://localhost:%d/parcels".formatted(server.getAddress().getPort());
  }
}
