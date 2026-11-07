package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import jakarta.validation.Valid;
import org.openldes.ldesadmin.application.port.in.sandbox.PollUseCase;
import org.openldes.ldesadmin.application.port.in.sandbox.RunPipelineUseCase;
import org.openldes.ldesadmin.application.port.in.sandbox.SandboxUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The local LDES server and LDIO to experiment with, and what the simulation of a pipeline needs.
 */
@RestController
@RequestMapping("/api/v1/sandbox")
class SandboxController {

  private final SandboxUseCase sandboxUseCase;
  private final PollUseCase pollUseCase;
  private final RunPipelineUseCase runPipelineUseCase;

  SandboxController(
      SandboxUseCase sandboxUseCase,
      PollUseCase pollUseCase,
      RunPipelineUseCase runPipelineUseCase) {
    this.sandboxUseCase = sandboxUseCase;
    this.pollUseCase = pollUseCase;
    this.runPipelineUseCase = runPipelineUseCase;
  }

  @GetMapping
  SandboxWebModel get() {
    return SandboxWebModel.fromDomain(sandboxUseCase.get());
  }

  /**
   * Starts what does not run yet, and returns once both instances are ready. The first start pulls
   * the Docker images, which takes a few minutes.
   */
  @PostMapping
  SandboxWebModel start() {
    return SandboxWebModel.fromDomain(sandboxUseCase.start());
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void stop() {
    sandboxUseCase.stop();
  }

  /**
   * Fetches what an HTTP In Poller would receive, for the simulation. LDES-admin fetches it, not
   * the browser, which most APIs don't allow to read their responses (CORS). A POST, as the request
   * carries credentials.
   */
  @PostMapping("/poll")
  PolledContentWebModel poll(@Valid @RequestBody PollRequest request) {
    return PolledContentWebModel.fromDomain(pollUseCase.poll(request.toDomain()));
  }

  /**
   * Runs a pipeline on the LDI components LDIO runs, and answers what every component did. A
   * component that fails is a step with status {@code error}, not an error response.
   */
  @PostMapping("/runs")
  PipelineRunWebModel run(@Valid @RequestBody RunRequest request) {
    return PipelineRunWebModel.fromDomain(
        runPipelineUseCase.run(
            request.pipeline().toDomain(), request.toInput(), request.toOptions()));
  }
}
