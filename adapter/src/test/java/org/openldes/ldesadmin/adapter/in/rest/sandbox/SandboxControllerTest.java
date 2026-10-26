package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.AdapterFixtures;
import org.openldes.ldesadmin.application.port.in.sandbox.PollUseCase;
import org.openldes.ldesadmin.application.port.in.sandbox.SandboxUseCase;
import org.openldes.ldesadmin.application.port.out.runtime.LocalRuntimeException;
import org.openldes.ldesadmin.application.port.out.sandbox.PollException;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.sandbox.Poll;
import org.openldes.ldesadmin.model.sandbox.Poll.ClientCredentials;
import org.openldes.ldesadmin.model.sandbox.PolledContent;
import org.openldes.ldesadmin.model.sandbox.Sandbox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = SandboxController.class)
class SandboxControllerTest {

  private static final Instance SANDBOX_SERVER = Instance.local(InstanceType.LDES_SERVER,
      Sandbox.LDES_SERVER_NAME, URI.create("http://localhost:32769"),
      URI.create("http://sandbox-ldes-server:8080"), "container-1", true, AdapterFixtures.NOW);
  private static final Instance SANDBOX_LDIO = Instance.local(InstanceType.LDIO,
      Sandbox.LDIO_NAME, URI.create("http://localhost:32770"),
      URI.create("http://sandbox-ldio:8080"), "container-2", true, AdapterFixtures.NOW);

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private SandboxUseCase sandboxUseCase;

  @MockitoBean
  private PollUseCase pollUseCase;

  @Test
  void reportsAStoppedSandbox() {
    when(sandboxUseCase.get()).thenReturn(Sandbox.stopped());

    assertThat(mvc.get().uri("/api/v1/sandbox"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            {"state": "STOPPED", "ldesServer": null, "ldio": null,
             "eventStream": "sandbox-parcels", "ingestPipeline": "sandbox-ingest",
             "eventStreamUrl": null}
            """);
  }

  @Test
  void startsTheSandbox() {
    when(sandboxUseCase.start()).thenReturn(new Sandbox(SANDBOX_SERVER, SANDBOX_LDIO));

    assertThat(mvc.post().uri("/api/v1/sandbox"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            {"state": "RUNNING",
             "ldesServer": {"name": "Sandbox LDES server", "url": "http://localhost:32769",
               "internalUrl": "http://sandbox-ldes-server:8080", "origin": "LOCAL"},
             "ldio": {"name": "Sandbox LDIO"},
             "eventStreamUrl": "http://sandbox-ldes-server:8080/sandbox-parcels"}
            """);
  }

  @Test
  void reportsThatDockerIsNotAvailable() {
    when(sandboxUseCase.start()).thenThrow(new LocalRuntimeException(
        "Could not start a local LDES server: Docker is not running", null));

    assertThat(mvc.post().uri("/api/v1/sandbox"))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .bodyJson().extractingPath("$.detail")
        .isEqualTo("Could not start a local LDES server: Docker is not running");
  }

  @Test
  void stopsTheSandbox() {
    assertThat(mvc.delete().uri("/api/v1/sandbox")).hasStatus(HttpStatus.NO_CONTENT);

    verify(sandboxUseCase).stop();
  }

  @Test
  void pollsAUrlForTheSimulation() {
    when(pollUseCase.poll(any())).thenReturn(
        new PolledContent("application/json; charset=utf-8", "[{\"name\": \"Vrijdagmarkt\"}]"));

    assertThat(mvc.post().uri("/api/v1/sandbox/poll")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"url": "https://api.example.org/parkings?lang=en",
             "headers": {"X-API-KEY": "secret"},
             "clientCredentials": {"tokenEndpoint": "https://login.example.org/token",
               "clientId": "ldes-admin", "clientSecret": "s3cr3t"}}
            """))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            {"contentType": "application/json; charset=utf-8",
             "body": "[{\\"name\\": \\"Vrijdagmarkt\\"}]"}
            """);

    verify(pollUseCase).poll(new Poll(URI.create("https://api.example.org/parkings?lang=en"),
        Map.of("X-API-KEY", "secret"),
        new ClientCredentials(URI.create("https://login.example.org/token"), "ldes-admin",
            "s3cr3t", null)));
  }

  @Test
  void refusesToPollWhatIsNotAnHttpUrl() {
    assertThat(mvc.post().uri("/api/v1/sandbox/poll")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"url\": \"file:///etc/passwd\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson().extractingPath("$.detail")
        .isEqualTo("'url' must be an absolute http(s) URL, but was file:///etc/passwd");
  }

  @Test
  void reportsAFailedPollAsBadGateway() {
    when(pollUseCase.poll(any())).thenThrow(new PollException(
        "https://api.example.org/parkings answered 404 NOT_FOUND"));

    assertThat(mvc.post().uri("/api/v1/sandbox/poll")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"url\": \"https://api.example.org/parkings\"}"))
        .hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson().extractingPath("$.detail")
        .isEqualTo("https://api.example.org/parkings answered 404 NOT_FOUND");
  }
}
