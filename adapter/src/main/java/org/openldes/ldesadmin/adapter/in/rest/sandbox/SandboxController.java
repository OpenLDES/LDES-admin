package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import org.openldes.ldesadmin.application.port.in.sandbox.SandboxUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The local LDES server and LDIO to experiment with.
 */
@RestController
@RequestMapping("/api/v1/sandbox")
class SandboxController {

  private final SandboxUseCase sandboxUseCase;

  SandboxController(SandboxUseCase sandboxUseCase) {
    this.sandboxUseCase = sandboxUseCase;
  }

  @GetMapping
  SandboxWebModel get() {
    return SandboxWebModel.fromDomain(sandboxUseCase.get());
  }

  /**
   * Starts what does not run yet, and returns once both instances are ready. The first start
   * pulls the Docker images, which takes a few minutes.
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
}
