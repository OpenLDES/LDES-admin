package org.openldes.ldesadmin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Exercises the whole hexagon: REST API, application services and Postgres. The registered LDES
 * server doesn't exist, which shows how unreachable instances are reported.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class LdesAdminApiIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {
      };

  @Autowired
  private RestTestClient client;

  @LocalServerPort
  private int port;

  @Test
  void registersMonitorsAndRemovesAnLdesServer() {
    Map<String, Object> registered = client.post().uri("/api/v1/ldes-servers")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("name", "Unreachable", "url", "http://localhost:1"))
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().exists("Location")
        .expectBody(JSON_OBJECT)
        .returnResult().getResponseBody();
    assertThat(registered).containsEntry("origin", "REGISTERED");
    Object id = registered.get("id");

    client.get().uri("/api/v1/ldes-servers").exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$[0].name").isEqualTo("Unreachable");

    client.get().uri("/api/v1/ldes-servers/{id}/health", id).exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.status").isEqualTo("DOWN");

    client.get().uri("/api/v1/dashboard").exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.ldesServers[0].server.name").isEqualTo("Unreachable")
        .jsonPath("$.ldesServers[0].health.status").isEqualTo("DOWN");

    client.get().uri("/api/v1/ldes-servers/{id}/eventstreams", id).exchange()
        .expectStatus().isEqualTo(502)
        .expectBody().jsonPath("$.detail")
        .isEqualTo("LDES server 'Unreachable' could not list event streams: "
            + "http://localhost:1 is unreachable");

    client.post().uri("/api/v1/ldes-servers")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("name", "Again", "url", "http://localhost:1/"))
        .exchange()
        .expectStatus().isEqualTo(409);

    client.delete().uri("/api/v1/ldes-servers/{id}", id).exchange()
        .expectStatus().isNoContent();
    client.get().uri("/api/v1/ldes-servers/{id}", id).exchange()
        .expectStatus().isNotFound();
  }

  @Test
  void doesNotMixUpLdesServersAndLdios() {
    Map<String, Object> ldio = client.post().uri("/api/v1/ldios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("name", "LDIO", "url", "http://localhost:2"))
        .exchange()
        .expectStatus().isCreated()
        .expectBody(JSON_OBJECT)
        .returnResult().getResponseBody();

    client.get().uri("/api/v1/ldes-servers/{id}", ldio.get("id")).exchange()
        .expectStatus().isNotFound();
    client.get().uri("/api/v1/ldios/{id}", ldio.get("id")).exchange()
        .expectStatus().isOk();
  }

  /**
   * LDES-admin itself is the API that is polled: its health endpoint.
   */
  @Test
  void pollsAUrlForTheSandbox() {
    client.post().uri("/api/v1/sandbox/poll")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("url", "http://localhost:%d/actuator/health".formatted(port)))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.contentType").isEqualTo("application/vnd.spring-boot.actuator.v3+json")
        .jsonPath("$.body").value(String.class, body -> assertThat(body).contains("\"UP\""));

    client.post().uri("/api/v1/sandbox/poll")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("url", "http://localhost:%d/actuator/nothing".formatted(port)))
        .exchange()
        .expectStatus().isEqualTo(502)
        .expectBody().jsonPath("$.detail").value(String.class, detail -> assertThat(detail)
            .startsWith("http://localhost:%d/actuator/nothing answered 404".formatted(port)));
  }
}
