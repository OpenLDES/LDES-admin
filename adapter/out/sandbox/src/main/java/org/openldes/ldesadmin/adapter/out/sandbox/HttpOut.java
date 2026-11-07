package org.openldes.ldesadmin.adapter.out.sandbox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.jena.rdf.model.Model;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldi.rdf.formatter.LdiRdfWriter;

/** Side-effect-free HTTP output preview, using LDIO's writer without applying a JSON-LD frame. */
final class HttpOut implements Output {

  private final String endpoint;
  private final String contentType;
  private final LdiRdfWriter writer;

  HttpOut(LdioConfig config) {
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
    return new OutputResult(
        serialisation, contentType, "Would POST %s to %s".formatted(contentType, endpoint));
  }
}
