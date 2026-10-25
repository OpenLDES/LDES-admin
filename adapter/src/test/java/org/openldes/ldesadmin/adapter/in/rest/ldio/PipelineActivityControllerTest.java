package org.openldes.ldesadmin.adapter.in.rest.ldio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.AdapterFixtures;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineActivityUseCase;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldio.LdesClientStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = PipelineActivityController.class)
class PipelineActivityControllerTest {

  @Autowired
  private MockMvcTester mvc;

  @MockitoBean
  private PipelineActivityUseCase pipelineActivityUseCase;

  private final Instance ldio = AdapterFixtures.ldio();

  @Test
  void reportsTheProgressOfLdesClients() {
    when(pipelineActivityUseCase.ldesClientStatuses(ldio.id()))
        .thenReturn(Map.of("replicate", LdesClientStatus.SYNCHRONISING));

    assertThat(mvc.get().uri("/api/v1/ldios/{id}/ldes-clients", ldio.id()))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("""
            {"replicate": "SYNCHRONISING"}
            """);
  }

  @Test
  void sendsAMemberToAPipeline() {
    String member = "<http://example.org/parcel/1> a <http://example.org/Parcel> .";

    assertThat(mvc.post().uri("/api/v1/ldios/{id}/pipelines/sandbox-ingest/members",
        ldio.id()).contentType("text/turtle").content(member))
        .hasStatus(HttpStatus.ACCEPTED);

    verify(pipelineActivityUseCase).send(eq(ldio.id()), eq("sandbox-ingest"), eq(member),
        startsWith("text/turtle"));
  }

  @Test
  void sendsJsonForAPipelineWithAJsonAdapter() {
    String message = "{\"capakey\": \"24504D0693/00B000\"}";

    assertThat(mvc.post().uri("/api/v1/ldios/{id}/pipelines/parcels/members", ldio.id())
        .contentType("application/json").content(message))
        .hasStatus(HttpStatus.ACCEPTED);

    verify(pipelineActivityUseCase).send(eq(ldio.id()), eq("parcels"), eq(message),
        startsWith("application/json"));
  }
}
