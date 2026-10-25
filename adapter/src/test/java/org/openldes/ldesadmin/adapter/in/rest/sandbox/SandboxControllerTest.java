package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.AdapterFixtures;
import org.openldes.ldesadmin.application.port.in.sandbox.SandboxUseCase;
import org.openldes.ldesadmin.application.port.out.runtime.LocalRuntimeException;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.sandbox.Sandbox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
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
}
