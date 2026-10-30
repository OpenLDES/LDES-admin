package org.openldes.ldesadmin.adapter.in.rest.ldio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.openldes.ldesadmin.application.port.in.ldio.ComponentCatalogUseCase;
import org.openldes.ldesadmin.application.port.in.ldio.IllegalPipelineStateException;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineLifecycleUseCase;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineStatisticsUseCase;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatistics;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.openldes.ldesadmin.model.ldio.StatusChangeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = {PipelineController.class, LdioInsightsController.class})
class PipelineControllerTest {

  private static final InstanceId LDIO = InstanceId.of("0c8a5f43-0a9e-4c39-8e0f-6f2f7d3c2b10");
  private static final String PIPELINE_JSON =
      """
      {
        "name": "parcels",
        "description": "Parcels to LDES",
        "input": {
          "name": "Ldio:HttpIn",
          "adapter": {"name": "Ldio:RdfAdapter"}
        },
        "transformers": [
          {"name": "Ldio:VersionObjectCreator", "config": {"member-type": "https://example.org/Parcel"}}
        ],
        "outputs": [
          {"name": "Ldio:HttpOut", "config": {"endpoint": "http://ldes:8080/parcels",
                                              "rdf-writer": {"content-type": "text/turtle"}}}
        ]
      }
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private PipelineUseCase pipelineUseCase;
  @MockitoBean private PipelineLifecycleUseCase pipelineLifecycleUseCase;
  @MockitoBean private ComponentCatalogUseCase componentCatalogUseCase;
  @MockitoBean private PipelineStatisticsUseCase pipelineStatisticsUseCase;

  @Test
  void createsAPipelineFromItsJsonDefinition() {
    when(pipelineUseCase.create(eq(LDIO), any()))
        .thenAnswer(
            invocation ->
                new DeployedPipeline(
                    invocation.getArgument(1), PipelineStatus.INIT, StatusChangeSource.AUTO));

    assertThat(
            mvc.post()
                .uri("/api/v1/ldios/{id}/pipelines", LDIO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PIPELINE_JSON))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": "INIT", "pipeline": {"name": "parcels",
             "input": {"name": "Ldio:HttpIn", "adapter": {"name": "Ldio:RdfAdapter"}}}}
            """);

    ArgumentCaptor<Pipeline> pipeline = ArgumentCaptor.forClass(Pipeline.class);
    Mockito.verify(pipelineUseCase).create(eq(LDIO), pipeline.capture());
    assertThat(pipeline.getValue().transformers())
        .containsExactly(
            new ComponentDefinition(
                "Ldio:VersionObjectCreator", Map.of("member-type", "https://example.org/Parcel")));
    assertThat(pipeline.getValue().outputs().getFirst().config())
        .containsEntry("rdf-writer", Map.of("content-type", "text/turtle"));
  }

  @Test
  void rejectsPipelineNamesThatCannotBeUsedInUrls() {
    assertThat(
            mvc.post()
                .uri("/api/v1/ldios/{id}/pipelines", LDIO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PIPELINE_JSON.replace("\"parcels\"", "\"my parcels\"")))
        .hasStatus(HttpStatus.BAD_REQUEST);

    verifyNoInteractions(pipelineUseCase);
  }

  @Test
  void doesNotRenameAPipeline() {
    assertThat(
            mvc.put()
                .uri("/api/v1/ldios/{id}/pipelines/addresses", LDIO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PIPELINE_JSON))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("cannot be renamed");
  }

  @Test
  void reportsAnImpossibleStatusChangeAsConflict() {
    when(pipelineLifecycleUseCase.halt(LDIO, "parcels"))
        .thenThrow(new IllegalPipelineStateException("parcels", PipelineStatus.HALTED, "halt"));

    assertThat(mvc.post().uri("/api/v1/ldios/{id}/pipelines/parcels/halt", LDIO))
        .hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void resumesAPipeline() {
    when(pipelineLifecycleUseCase.resume(LDIO, "parcels")).thenReturn(PipelineStatus.RUNNING);

    assertThat(mvc.post().uri("/api/v1/ldios/{id}/pipelines/parcels/resume", LDIO))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"pipeline\": \"parcels\", \"status\": \"RUNNING\"}");
  }

  @Test
  void reportsThroughputPerPipeline() {
    when(pipelineStatisticsUseCase.statistics(LDIO))
        .thenReturn(List.of(new PipelineStatistics("parcels", 120, 118)));

    assertThat(mvc.get().uri("/api/v1/ldios/{id}/statistics", LDIO))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            [{"pipeline": "parcels", "membersIn": 120, "membersOut": 118, "inFlight": 2}]
            """);
  }
}
