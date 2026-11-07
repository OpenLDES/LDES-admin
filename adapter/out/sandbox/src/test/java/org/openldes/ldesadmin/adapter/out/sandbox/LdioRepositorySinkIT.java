package org.openldes.ldesadmin.adapter.out.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class LdioRepositorySinkIT {
  @Test
  void writesThroughTheRealRepositorySinkAndClosesItsSession() {
    try (GenericContainer<?> repository =
        new GenericContainer<>(DockerImageName.parse("eclipse/rdf4j-workbench:4.3.4"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/rdf4j-server/protocol"))) {
      repository.start();
      String endpoint =
          "http://" + repository.getHost() + ":" + repository.getMappedPort(8080) + "/rdf4j-server";
      RestClient client = RestClient.create(endpoint);
      client
          .put()
          .uri("/repositories/sandbox")
          .contentType(MediaType.valueOf("text/turtle"))
          .body(
              """
              @prefix rep: <http://www.openrdf.org/config/repository#> .
              @prefix sr: <http://www.openrdf.org/config/repository/sail#> .
              @prefix sail: <http://www.openrdf.org/config/sail#> .
              [] a rep:Repository ; rep:repositoryID "sandbox" ;
                 rep:repositoryImpl [
                   rep:repositoryType "openrdf:SailRepository" ;
                   sr:sailImpl [ sail:sailType "openrdf:MemoryStore" ]
                 ] .
              """)
          .retrieve()
          .toBodilessEntity();
      LdiComponentRuntime runtime =
          new LdiComponentRuntime(Duration.ofSeconds(20), Clock.systemUTC());
      try {
        var sink =
            new ComponentDefinition(
                "Ldio:RepositorySink", Map.of("sparql-host", endpoint, "repository-id", "sandbox"));
        runtime.write(
            "repository-it",
            0,
            sink,
            RdfDocument.turtle("<http://example.org/a> <http://example.org/b> \"real sink\" ."),
            true);

        assertThat(
                client
                    .get()
                    .uri("/repositories/sandbox/statements")
                    .accept(MediaType.valueOf("text/turtle"))
                    .retrieve()
                    .body(String.class))
            .contains("http://example.org/a", "real sink");
        runtime.release("repository-it");
      } finally {
        runtime.destroy();
      }
    }
  }
}
