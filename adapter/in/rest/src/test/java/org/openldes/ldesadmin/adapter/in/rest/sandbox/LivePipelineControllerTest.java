package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.application.port.in.sandbox.LivePipelineUseCase;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.model.sandbox.LivePipelineRun;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldesadmin.model.sandbox.RunOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = LivePipelineController.class)
class LivePipelineControllerTest {
  @Autowired private MockMvcTester mvc;
  @MockitoBean private LivePipelineUseCase useCase;

  @Test
  void startsDryUnlessOutputExecutionIsExplicitlyRequested() {
    when(useCase.start(any(), any())).thenReturn(new LivePipelineRun(true, 0, null, null));

    assertThat(
            mvc.post()
                .uri("/api/v1/sandbox/live")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"session":"browser","pipeline":{"name":"live","input":{"name":"Ldio:HttpIn",
            "adapter":{"name":"Ldio:RdfAdapter"}},"outputs":[{"name":"Ldio:NoopOut"}]}}
            """))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"active":true,"received":0,"latest":null,"error":null}
            """);
    verify(useCase).start(any(), org.mockito.ArgumentMatchers.eq(new RunOptions("browser", false)));
  }

  @Test
  void exposesStatusAndAcceptsHttpMembers() {
    when(useCase.get("browser")).thenReturn(new LivePipelineRun(false, 2, null, "Broker stopped"));

    assertThat(mvc.get().uri("/api/v1/sandbox/live/browser"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.error")
        .isEqualTo("Broker stopped");
    assertThat(
            mvc.post()
                .uri("/api/v1/sandbox/live/browser/input")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"content":"<a> <b> <c> .","contentType":"text/turtle"}
            """))
        .hasStatus(HttpStatus.ACCEPTED);
    verify(useCase).send("browser", RunInput.of("<a> <b> <c> .", "text/turtle"));
  }

  @Test
  void forwardsConnectorCallbacksAsRawJson() {
    String token = "{\"authKey\":\"Authorization\",\"authCode\":\"test\"}";
    when(useCase.connector("browser", "token", token)).thenReturn("");

    assertThat(
            mvc.post()
                .uri("/api/v1/sandbox/live/browser/connector/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(token))
        .hasStatusOk();
    verify(useCase).connector("browser", "token", token);
  }

  @Test
  void rejectsInvalidRequestsAndReportsStartupErrors() {
    assertThat(
            mvc.post()
                .uri("/api/v1/sandbox/live")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"session\":\"\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    when(useCase.get("browser")).thenThrow(new ComponentException("Could not start Kafka"));
    assertThat(mvc.get().uri("/api/v1/sandbox/live/browser"))
        .hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("Could not start Kafka");
  }

  @Test
  void stopsTheSession() {
    assertThat(mvc.delete().uri("/api/v1/sandbox/live/browser")).hasStatus(HttpStatus.NO_CONTENT);
    verify(useCase).stop("browser");
  }
}
