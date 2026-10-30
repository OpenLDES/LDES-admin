package org.openldes.ldesadmin.adapter.out.sandbox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldi.rdf.formatter.LdiRdfWriter;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.PostRequest;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeader;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

/**
 * The HTTP output of LDIO ({@code LdioHttpOut}): posts every model to its endpoint, in the format
 * of its RDF writer. Like LDIO, it writes JSON-LD without applying a frame, and skips empty models.
 */
final class HttpOut implements Output {

  private static final int MAX_ERROR_LENGTH = 500;

  private final LdioConfig config;
  private final String endpoint;
  private final String contentType;
  private final LdiRdfWriter writer;

  /** Only made when the output is executed: a dry run needs no credentials */
  private RequestExecutor requestExecutor;

  HttpOut(LdioConfig config) {
    this.config = config;
    this.endpoint = config.property("endpoint");
    Output.RdfWriter rdfWriter = Output.RdfWriter.of(config);
    this.contentType = rdfWriter.contentType();
    this.writer = rdfWriter.writer();
  }

  @Override
  public OutputResult write(Model model, boolean execute) {
    if (model.isEmpty()) {
      return new OutputResult("", contentType, "Sent nothing: the model is empty");
    }
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    writer.writeToOutputStream(model, body);
    String serialisation = body.toString(StandardCharsets.UTF_8);
    if (!execute) {
      return new OutputResult(
          serialisation, contentType, "Would POST %s to %s".formatted(contentType, endpoint));
    }
    Response response =
        requestExecutor()
            .execute(
                new PostRequest(
                    endpoint,
                    new RequestHeaders(List.of(new RequestHeader("Content-Type", contentType))),
                    body.toByteArray()));
    if (!response.isSuccess()) {
      String answer = response.getBodyAsString().orElse("").strip();
      throw new ComponentException(
          "POST %s answered %d%s"
              .formatted(
                  endpoint,
                  response.getHttpStatus(),
                  answer.isEmpty()
                      ? ""
                      : " - " + answer.substring(0, Math.min(answer.length(), MAX_ERROR_LENGTH))));
    }
    return new OutputResult(
        serialisation, contentType, "POST %s: %d".formatted(endpoint, response.getHttpStatus()));
  }

  private synchronized RequestExecutor requestExecutor() {
    if (requestExecutor == null) {
      requestExecutor = RequestExecutors.create(config);
    }
    return requestExecutor;
  }
}
