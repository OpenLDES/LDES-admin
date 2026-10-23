package org.openldes.ldesadmin.adapter.in.rest.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.AdapterFixtures;
import org.openldes.ldesadmin.application.port.in.instance.CheckInstanceHealthUseCase;
import org.openldes.ldesadmin.application.port.in.instance.FindInstancesUseCase;
import org.openldes.ldesadmin.application.port.in.instance.InstanceAlreadyRegisteredException;
import org.openldes.ldesadmin.application.port.in.instance.InstanceNotFoundException;
import org.openldes.ldesadmin.application.port.in.instance.RegisterInstanceUseCase;
import org.openldes.ldesadmin.application.port.in.instance.RegisterInstanceUseCase.RegisterInstanceCommand;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase;
import org.openldes.ldesadmin.application.port.in.instance.UnregisterInstanceUseCase;
import org.openldes.ldesadmin.application.port.out.runtime.LocalRuntimeException;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = {LdesServerController.class, LdioController.class})
@Import(InstanceUseCases.class)
class InstanceControllerTest {

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private FindInstancesUseCase findInstancesUseCase;
  @MockitoBean
  private RegisterInstanceUseCase registerInstanceUseCase;
  @MockitoBean
  private StartLocalInstanceUseCase startLocalInstanceUseCase;
  @MockitoBean
  private UnregisterInstanceUseCase unregisterInstanceUseCase;
  @MockitoBean
  private CheckInstanceHealthUseCase checkInstanceHealthUseCase;

  private final Instance server = AdapterFixtures.ldesServer();

  @Test
  void listsTheLdesServers() {
    when(findInstancesUseCase.findAll(InstanceType.LDES_SERVER)).thenReturn(List.of(server));

    assertThat(mvc.get().uri("/api/v1/ldes-servers"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            [{
              "id": "%s",
              "type": "LDES_SERVER",
              "name": "Production",
              "url": "http://ldes.example.org",
              "origin": "REGISTERED",
              "sandbox": false,
              "registeredAt": "2026-10-23T10:15:30Z"
            }]
            """.formatted(server.id()));
  }

  @Test
  void registersAnLdioAndPointsToIt() {
    Instance ldio = AdapterFixtures.ldio();
    when(registerInstanceUseCase.register(any())).thenReturn(ldio);

    assertThat(mvc.post().uri("/api/v1/ldios")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"name": "Ingest LDIO", "url": "http://ldio.example.org", "sandbox": false}
            """))
        .hasStatus(HttpStatus.CREATED)
        .satisfies(result -> assertThat(result.getResponse().getHeader("Location"))
            .isEqualTo("http://localhost/api/v1/ldios/" + ldio.id()))
        .bodyJson().extractingPath("$.type").isEqualTo("LDIO");

    verify(registerInstanceUseCase).register(new RegisterInstanceCommand(InstanceType.LDIO,
        "Ingest LDIO", URI.create("http://ldio.example.org"), false));
  }

  @Test
  void rejectsARegistrationWithoutName() {
    assertThat(mvc.post().uri("/api/v1/ldes-servers")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"url\": \"http://ldes.example.org\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST);

    verifyNoInteractions(registerInstanceUseCase);
  }

  @Test
  void reportsADuplicateRegistrationAsConflict() {
    when(registerInstanceUseCase.register(any())).thenThrow(
        new InstanceAlreadyRegisteredException(InstanceType.LDES_SERVER, server.url()));

    assertThat(mvc.post().uri("/api/v1/ldes-servers")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\": \"Production\", \"url\": \"http://ldes.example.org\"}"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson().extractingPath("$.detail")
        .isEqualTo("A LDES_SERVER with URL http://ldes.example.org is already registered");
  }

  @Test
  void reportsAnUnknownServerAsNotFound() {
    InstanceId id = InstanceId.randomId();
    when(findInstancesUseCase.get(InstanceType.LDES_SERVER, id))
        .thenThrow(new InstanceNotFoundException(InstanceType.LDES_SERVER, id));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}", id))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson().extractingPath("$.status").isEqualTo(404);
  }

  @Test
  void reportsThatALocalInstanceCouldNotBeStarted() {
    when(startLocalInstanceUseCase.start(any())).thenThrow(new LocalRuntimeException(
        "Could not start a local LDIO: Docker is not running", null));

    assertThat(mvc.post().uri("/api/v1/ldios/local")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\": \"Sandbox\", \"sandbox\": true}"))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void unregistersAnInstance() {
    assertThat(mvc.delete().uri("/api/v1/ldes-servers/{id}", server.id()))
        .hasStatus(HttpStatus.NO_CONTENT);

    verify(unregisterInstanceUseCase).unregister(InstanceType.LDES_SERVER, server.id());
  }

  @Test
  void reportsTheHealthOfAnInstance() {
    when(checkInstanceHealthUseCase.check(InstanceType.LDES_SERVER, server.id()))
        .thenReturn(InstanceHealth.up(AdapterFixtures.NOW));

    assertThat(mvc.get().uri("/api/v1/ldes-servers/{id}/health", server.id()))
        .hasStatusOk()
        .bodyJson().extractingPath("$.status").isEqualTo("UP");
  }
}
