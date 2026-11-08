package org.openldes.ldesadmin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Serves the frontend as the frontend module built it. Skipped when the frontend was not built
 * (-Dfrontend.skip).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class FrontendIT {

  private static final Pattern SCRIPT = Pattern.compile("src=\"(/assets/[^\"]+\\.js)\"");

  @Autowired private RestTestClient client;

  @LocalServerPort private int port;

  @BeforeEach
  void requireTheBuiltFrontend() {
    assumeTrue(
        new ClassPathResource("static/index.html").exists(),
        "The frontend was not built, so there is nothing to serve");
  }

  @Test
  void servesTheFrontendOnTheRoot() {
    String html = index("/");

    assertThat(html).contains("<div id=\"app\"></div>", "<title>LDES-admin</title>");
  }

  @Test
  void servesTheFrontendForItsOwnRoutes() {
    assertThat(index("/ldes-servers/6f3c1c1e-3f0a-4d44-9d1c-2a8d0e7c9b11/eventstreams/parcels"))
        .isEqualTo(index("/"));
    assertThat(index("/sandbox")).isEqualTo(index("/"));
  }

  @Test
  void letsBrowsersKeepTheAssetsButNotTheIndex() {
    Matcher script = SCRIPT.matcher(index("/"));
    assertThat(script.find()).isTrue();

    client
        .get()
        .uri(script.group(1))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith("text/javascript")
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable");
    client
        .get()
        .uri("/")
        .exchange()
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache");
  }

  @Test
  void compressesWhatItServes() throws Exception {
    Matcher script = SCRIPT.matcher(index("/"));
    assertThat(script.find()).isTrue();

    // The test client decompresses on the fly; the JDK client shows what goes over the wire
    try (HttpClient http = HttpClient.newHttpClient()) {
      HttpResponse<Void> response =
          http.send(
              HttpRequest.newBuilder()
                  .uri(URI.create("http://localhost:" + port + script.group(1)))
                  .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                  .build(),
              HttpResponse.BodyHandlers.discarding());

      assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).contains("gzip");
    }
  }

  @Test
  void doesNotAnswerMissingFilesOrEndpointsWithTheFrontend() {
    client.get().uri("/assets/index-missing.js").exchange().expectStatus().isNotFound();
    client.get().uri("/missing.png").exchange().expectStatus().isNotFound();
    client
        .get()
        .uri("/api/v1/missing")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private String index(String path) {
    return client
        .get()
        .uri(path)
        .accept(MediaType.TEXT_HTML)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_HTML)
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }
}
