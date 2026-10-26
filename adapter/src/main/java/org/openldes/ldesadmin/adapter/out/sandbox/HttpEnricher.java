package org.openldes.ldesadmin.adapter.out.sandbox;

import java.util.List;
import java.util.Optional;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.openldes.ldi.extractor.EmptyPropertyExtractor;
import org.openldes.ldi.extractor.PropertyExtractor;
import org.openldes.ldi.extractor.PropertyPathExtractor;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.GetRequest;
import org.openldes.ldi.requestexecutor.valueobjects.PostRequest;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeader;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.types.LdiAdapter;

/**
 * The HTTP enricher of LDIO ({@code LdioHttpEnricher}), which only exists in LDIO itself: it
 * requests the URL found in the model (with the method, headers and body found there too), turns
 * the response into linked data with its adapter, and adds that to the model.
 */
final class HttpEnricher {

  private final LdiAdapter adapter;
  private final RequestExecutor requestExecutor;
  private final PropertyExtractor url;
  private final PropertyExtractor body;
  private final PropertyExtractor headers;
  private final PropertyExtractor httpMethod;

  HttpEnricher(LdioConfig config) {
    this.adapter = Adapters.create(config.property("adapter.name"),
        config.nestedConfig("adapter.config", config.property("adapter.name")));
    this.requestExecutor = RequestExecutors.create(config);
    this.url = PropertyPathExtractor.from(config.property("url-property-path"));
    this.body = extractor(config, "body-property-path");
    this.headers = extractor(config, "header-property-path");
    this.httpMethod = extractor(config, "http-method-property-path");
  }

  List<Model> transform(Model model) {
    Response response = requestExecutor.execute(request(model));
    if (!response.isSuccess()) {
      // As LDIO, which logs a warning and passes the model on as it is
      return List.of(model);
    }
    response.getBodyAsString().stream()
        .flatMap(content -> adapter.apply(LdiAdapter.Content.of(content, mimeType(response))))
        .toList()
        .forEach(model::add);
    return List.of(model);
  }

  private Request request(Model model) {
    String target = first(url, model).orElseThrow(() ->
        new IllegalArgumentException("No url found on the defined property path."));
    RequestHeaders requestHeaders = new RequestHeaders(headers.getProperties(model).stream()
        .map(RDFNode::toString)
        .map(RequestHeader::from)
        .toList());
    String method = first(httpMethod, model).map(String::toUpperCase).orElse("GET");
    return switch (method) {
      case "GET" -> new GetRequest(target, requestHeaders);
      case "POST" -> new PostRequest(target, requestHeaders,
          StringEscapeUtils.unescapeJava(first(body, model).orElse(null)));
      default -> throw new IllegalStateException("Http method not supported: " + method);
    };
  }

  private static Optional<String> first(PropertyExtractor extractor, Model model) {
    return extractor.getProperties(model).stream().findFirst().map(RDFNode::toString);
  }

  private static String mimeType(Response response) {
    return response.getFirstHeaderValue("Content-Type")
        .map(ContentType::parse)
        .map(ContentType::getMimeType)
        .orElse(ContentType.TEXT_PLAIN.getMimeType());
  }

  private static PropertyExtractor extractor(LdioConfig config, String key) {
    return config.optional(key)
        .map(PropertyPathExtractor::from)
        .map(PropertyExtractor.class::cast)
        .orElseGet(EmptyPropertyExtractor::new);
  }
}
