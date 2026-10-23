package org.openldes.ldesadmin.adapter.in.rest;

import org.openldes.ldesadmin.application.port.in.instance.InstanceAlreadyRegisteredException;
import org.openldes.ldesadmin.application.port.in.instance.InstanceNotFoundException;
import org.openldes.ldesadmin.application.port.in.ldio.IllegalPipelineStateException;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineNotFoundException;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.application.port.out.runtime.LocalRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions of the application ring into RFC 9457 problem details.
 */
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler({InstanceNotFoundException.class, PipelineNotFoundException.class})
  ProblemDetail handleNotFound(RuntimeException e) {
    return problem(HttpStatus.NOT_FOUND, e);
  }

  @ExceptionHandler({InstanceAlreadyRegisteredException.class,
      IllegalPipelineStateException.class})
  ProblemDetail handleConflict(RuntimeException e) {
    return problem(HttpStatus.CONFLICT, e);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidInput(IllegalArgumentException e) {
    return problem(HttpStatus.BAD_REQUEST, e);
  }

  /**
   * Errors of a remote LDES server or LDIO. Rejections are reported as bad requests, because they
   * are caused by what the client sent (e.g. invalid RDF).
   */
  @ExceptionHandler(RemoteInstanceException.class)
  ProblemDetail handleRemoteInstance(RemoteInstanceException e) {
    HttpStatus status = switch (e.reason()) {
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case REJECTED -> HttpStatus.BAD_REQUEST;
      case UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
    };
    return problem(status, e);
  }

  @ExceptionHandler(LocalRuntimeException.class)
  ProblemDetail handleLocalRuntime(LocalRuntimeException e) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, e);
  }

  private static ProblemDetail problem(HttpStatus status, RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(status, e.getMessage());
  }
}
