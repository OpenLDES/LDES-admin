package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import jakarta.validation.Valid;
import org.openldes.ldesadmin.application.port.in.sandbox.LivePipelineUseCase;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sandbox/live")
class LivePipelineController {
  private final LivePipelineUseCase useCase;

  LivePipelineController(LivePipelineUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping
  LiveRunWebModel start(@Valid @RequestBody LiveRunRequest request) {
    return LiveRunWebModel.fromDomain(
        useCase.start(request.pipeline().toDomain(), request.toOptions()));
  }

  @GetMapping("/{session}")
  LiveRunWebModel get(@PathVariable String session) {
    return LiveRunWebModel.fromDomain(useCase.get(session));
  }

  @PostMapping("/{session}/input")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void send(@PathVariable String session, @Valid @RequestBody RunRequest.InputRequest request) {
    useCase.send(session, new RunInput(request.content(), request.contentType(), request.source()));
  }

  @PostMapping(
      value = "/{session}/connector/{action}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  String connector(
      @PathVariable String session, @PathVariable String action, @RequestBody String content) {
    return useCase.connector(session, action, content);
  }

  @DeleteMapping("/{session}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void stop(@PathVariable String session) {
    useCase.stop(session);
  }
}
