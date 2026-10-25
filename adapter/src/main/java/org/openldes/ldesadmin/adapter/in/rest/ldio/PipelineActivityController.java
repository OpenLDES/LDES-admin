package org.openldes.ldesadmin.adapter.in.rest.ldio;

import java.util.Map;
import java.util.UUID;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineActivityUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.LdesClientStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * What goes through the pipelines of an LDIO: the progress of its LDES clients, and members sent
 * to its HTTP inputs.
 */
@RestController
@RequestMapping("/api/v1/ldios/{ldioId}")
class PipelineActivityController {

  private final PipelineActivityUseCase pipelineActivityUseCase;

  PipelineActivityController(PipelineActivityUseCase pipelineActivityUseCase) {
    this.pipelineActivityUseCase = pipelineActivityUseCase;
  }

  /**
   * The status of every pipeline with an LDES client input, by pipeline name.
   */
  @GetMapping("/ldes-clients")
  Map<String, LdesClientStatus> ldesClients(@PathVariable UUID ldioId) {
    return pipelineActivityUseCase.ldesClientStatuses(new InstanceId(ldioId));
  }

  /**
   * Sends a message to a pipeline that starts with an {@code Ldio:HttpIn} input. Any content type
   * goes: the adapter of the pipeline decides what it understands (RDF, JSON, CSV, ...).
   */
  @PostMapping("/pipelines/{pipeline}/members")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void send(@PathVariable UUID ldioId, @PathVariable String pipeline, @RequestBody String message,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    pipelineActivityUseCase.send(new InstanceId(ldioId), pipeline, message, contentType);
  }
}
