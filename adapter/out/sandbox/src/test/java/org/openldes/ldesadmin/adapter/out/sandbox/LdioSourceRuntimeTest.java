package org.openldes.ldesadmin.adapter.out.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.application.port.out.sandbox.LiveSourceRuntime;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.sandbox.RunInput;

class LdioSourceRuntimeTest {
  private static final RunInput MEMBER =
      RunInput.of("<http://example.org/a> <http://example.org/b> \"c\" .", "text/turtle");
  private final LdioSourceRuntime runtime = new LdioSourceRuntime(Duration.ofSeconds(20));
  private final BlockingQueue<RunInput> received = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> failures = new LinkedBlockingQueue<>();
  private HttpServer server;

  @AfterEach
  void close() {
    runtime.destroy();
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void feedsARealHttpInAndTerminatesItsWorker() throws InterruptedException {
    List<ProcessHandle> before = ProcessHandle.current().children().toList();
    LiveSourceRuntime.Source source =
        runtime.start(input("Ldio:HttpIn", Map.of()), received::add, failures::add);
    List<ProcessHandle> children =
        ProcessHandle.current().children().filter(child -> !before.contains(child)).toList();

    source.send(MEMBER);

    assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo(MEMBER);
    assertThat(children).hasSize(1);
    source.close();
    assertThat(children).noneMatch(ProcessHandle::isAlive);
    assertThatThrownBy(() -> source.send(MEMBER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The live source is stopped");
    assertThat(failures).isEmpty();
  }

  @Test
  void usesTheRealPollerScheduleAndStopsFetching() throws IOException, InterruptedException {
    String url = serve(200);
    LiveSourceRuntime.Source source =
        runtime.start(
            input("Ldio:HttpInPoller", Map.of("url", url, "interval", "PT0.2S")),
            received::add,
            failures::add);

    assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo(MEMBER);
    assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo(MEMBER);
    source.close();
    received.clear();
    assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
    assertThat(failures).isEmpty();
  }

  @Test
  void reportsLoggedUpstreamPollingFailures() throws IOException, InterruptedException {
    runtime.start(
        input(
            "Ldio:HttpInPoller",
            Map.of("url", serve(400), "interval", "PT0.1S", "retries", Map.of("enabled", false))),
        received::add,
        failures::add);

    assertThat(failures.poll(10, TimeUnit.SECONDS)).contains("400");
    assertThat(received).isEmpty();
  }

  @Test
  void followsALdesStreamUsingTheRealClient() throws IOException, InterruptedException {
    String url = ldesEndpoint();
    try (LiveSourceRuntime.Source source =
        runtime.start(
            input("Ldio:LdesClient", Map.of("urls", List.of(url))), received::add, failures::add)) {
      assertThat(received.poll(10, TimeUnit.SECONDS))
          .isNotNull()
          .extracting(RunInput::content)
          .asString()
          .contains("http://example.org/a", "\"c\"");
      assertThat(failures).isEmpty();
    }
  }

  @Test
  void bridgesTheRealConnectorTokenAndTransferApi() throws IOException, InterruptedException {
    AtomicReference<String> transfer = new AtomicReference<>();
    String url = ldesEndpoint();
    server.createContext(
        "/transfer",
        exchange -> {
          transfer.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body = "{\"id\":\"transfer-started\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    try (LiveSourceRuntime.Source source =
        runtime.start(
            input(
                "Ldio:LdesClientConnector",
                Map.of("urls", List.of(url), "connector-transfer-url", url + "/transfer")),
            received::add,
            failures::add)) {
      assertThat(source.connector("transfer", "{\"assetId\":\"sandbox\"}"))
          .contains("transfer-started");
      assertThat(transfer.get()).isEqualTo("{\"assetId\":\"sandbox\"}");
      assertThat(source.connector("token", "{\"authKey\":\"Authorization\",\"authCode\":\"test\"}"))
          .isEmpty();
      assertThat(received.poll(10, TimeUnit.SECONDS))
          .isNotNull()
          .extracting(RunInput::content)
          .asString()
          .contains("http://example.org/a");
      assertThat(failures).isEmpty();
    }
  }

  private String ldesEndpoint() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    String url = "http://localhost:" + server.getAddress().getPort();
    String document =
        """
        @prefix ldes: <https://w3id.org/ldes#> .
        @prefix tree: <https://w3id.org/tree#> .
        @prefix dcterms: <http://purl.org/dc/terms/> .
        @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
        <%s> a ldes:EventStream ; tree:view <%s/page> ;
          ldes:timestampPath dcterms:modified ; ldes:versionOfPath dcterms:isVersionOf ;
          tree:member <http://example.org/a> .
        <http://example.org/a> <http://example.org/b> "c" ;
          dcterms:isVersionOf <http://example.org/state> ;
          dcterms:modified "2026-10-24T10:15:30Z"^^xsd:dateTime .
        """
            .formatted(url, url);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = document.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "text/turtle");
          exchange.getResponseHeaders().set("Cache-Control", "max-age=3600");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return url;
  }

  @Test
  void rejectsInvalidInputConfigurationWithoutLeavingAWorker() {
    List<ProcessHandle> before = ProcessHandle.current().children().toList();

    assertThatThrownBy(
            () -> runtime.start(input("Ldio:HttpInPoller", Map.of()), received::add, failures::add))
        .isInstanceOf(ComponentException.class)
        .hasMessageContaining("url");

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        ProcessHandle.current().children().filter(child -> !before.contains(child)))
                    .isEmpty());
  }

  @Test
  void losingTheParentPipeTerminatesConnectorThreads() throws IOException, InterruptedException {
    Process worker =
        new ProcessBuilder(LdioSourceRuntime.command())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    try {
      DataOutputStream outbound = new DataOutputStream(worker.getOutputStream());
      SourceWire.write(
          outbound, input("Ldio:HttpInPoller", Map.of("url", serve(200), "interval", "PT1H")));
      DataInputStream inbound = new DataInputStream(worker.getInputStream());
      SourceWire.Message message;
      do {
        message = SourceWire.read(inbound, SourceWire.Message.class);
      } while (!message.type().equals("ready"));
      outbound.close();

      assertThat(worker.waitFor(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      worker.destroyForcibly();
    }
  }

  private static InputDefinition input(String name, Map<String, Object> config) {
    return new InputDefinition(name, config, ComponentDefinition.of("Ldio:RdfAdapter"));
  }

  private String serve(int status) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = MEMBER.content().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", MEMBER.contentType());
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort() + "/members";
  }
}
