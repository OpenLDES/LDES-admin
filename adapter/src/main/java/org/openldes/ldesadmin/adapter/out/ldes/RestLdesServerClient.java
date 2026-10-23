package org.openldes.ldesadmin.adapter.out.ldes;

import static org.openldes.ldesadmin.adapter.out.http.RemoteCalls.call;
import static org.openldes.ldesadmin.adapter.out.http.RemoteCalls.run;

import java.util.List;
import java.util.function.BiFunction;
import org.openldes.ldesadmin.adapter.out.http.InstanceRestClients;
import org.openldes.ldesadmin.adapter.out.http.PrometheusMetrics;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException;
import org.openldes.ldesadmin.application.port.out.RemoteInstanceException.Reason;
import org.openldes.ldesadmin.application.port.out.ldes.LdesServerClient;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.DcatTarget.EventStreamDataset;
import org.openldes.ldesadmin.model.ldes.DcatTarget.ServerCatalog;
import org.openldes.ldesadmin.model.ldes.DcatTarget.ViewDataService;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldes.View;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the admin API ({@code /admin/api/v1}), the ingest endpoint and the Prometheus metrics
 * of an LDES server.
 *
 * <p>Assumes the admin, ingest and fetch endpoints and the actuator share the base URL of the
 * server (the default, single-port deployment).
 */
@Component
public class RestLdesServerClient implements LdesServerClient {

  private static final MediaType TURTLE = MediaType.valueOf("text/turtle");
  private static final String EVENT_STREAMS = "/admin/api/v1/eventstreams";
  private static final String EVENT_STREAM = EVENT_STREAMS + "/{collection}";
  private static final String VIEWS = EVENT_STREAM + "/views";
  private static final String VIEW = VIEWS + "/{view}";
  private static final String DCAT = "/admin/api/v1/dcat";

  private final InstanceRestClients restClients;

  public RestLdesServerClient(InstanceRestClients restClients) {
    this.restClients = restClients;
  }

  @Override
  public List<EventStream> getEventStreams(Instance server) {
    String turtle = call(server, "list event streams", () -> rest(server).get()
        .uri(EVENT_STREAMS).accept(TURTLE)
        .retrieve().body(String.class));
    return read(server, turtle, LdesRdfReader::readEventStreams);
  }

  @Override
  public EventStream getEventStream(Instance server, String collection) {
    String turtle = call(server, "get event stream " + collection, () -> rest(server).get()
        .uri(EVENT_STREAM, collection).accept(TURTLE)
        .retrieve().body(String.class));
    return single(server, collection, read(server, turtle, LdesRdfReader::readEventStreams));
  }

  @Override
  public EventStream createEventStream(Instance server, RdfDocument definition) {
    String turtle = call(server, "create event stream", () -> rest(server).post()
        .uri(EVENT_STREAMS).contentType(mediaType(definition)).accept(TURTLE)
        .body(definition.content())
        .retrieve().body(String.class));
    List<EventStream> created = turtle == null || turtle.isBlank()
        ? LdesRdfReader.readEventStreams(definition, base(server))
        : read(server, turtle, LdesRdfReader::readEventStreams);
    return single(server, "created event stream", created);
  }

  @Override
  public void deleteEventStream(Instance server, String collection) {
    run(server, "delete event stream " + collection, () -> rest(server).delete()
        .uri(EVENT_STREAM, collection)
        .retrieve().toBodilessEntity());
  }

  @Override
  public List<View> getViews(Instance server, String collection) {
    String turtle = call(server, "list views of " + collection, () -> rest(server).get()
        .uri(VIEWS, collection).accept(TURTLE)
        .retrieve().body(String.class));
    return read(server, turtle,
        (document, base) -> LdesRdfReader.readViews(collection, document, base));
  }

  @Override
  public View getView(Instance server, String collection, String view) {
    String turtle = call(server, "get view " + view, () -> rest(server).get()
        .uri(VIEW, collection, view).accept(TURTLE)
        .retrieve().body(String.class));
    return single(server, view, read(server, turtle,
        (document, base) -> LdesRdfReader.readViews(collection, document, base)));
  }

  @Override
  public View createView(Instance server, String collection, RdfDocument definition) {
    String turtle = call(server, "create view on " + collection, () -> rest(server).post()
        .uri(VIEWS, collection).contentType(mediaType(definition)).accept(TURTLE)
        .body(definition.content())
        .retrieve().body(String.class));
    List<View> created = turtle == null || turtle.isBlank()
        ? LdesRdfReader.readViews(collection, definition, base(server))
        : read(server, turtle,
            (document, base) -> LdesRdfReader.readViews(collection, document, base));
    return single(server, "created view", created);
  }

  @Override
  public void deleteView(Instance server, String collection, String view) {
    run(server, "delete view " + view, () -> rest(server).delete()
        .uri(VIEW, collection, view)
        .retrieve().toBodilessEntity());
  }

  @Override
  public RdfDocument getShape(Instance server, String collection) {
    String turtle = call(server, "get shape of " + collection, () -> rest(server).get()
        .uri(EVENT_STREAM + "/shape", collection).accept(TURTLE)
        .retrieve().body(String.class));
    return RdfDocument.turtle(turtle == null ? "" : turtle);
  }

  @Override
  public RdfDocument updateShape(Instance server, String collection, RdfDocument shape) {
    String turtle = call(server, "update shape of " + collection, () -> rest(server).put()
        .uri(EVENT_STREAM + "/shape", collection).contentType(mediaType(shape)).accept(TURTLE)
        .body(shape.content())
        .retrieve().body(String.class));
    return turtle == null ? shape : RdfDocument.turtle(turtle);
  }

  @Override
  public RdfDocument getCatalog(Instance server) {
    String turtle = call(server, "get DCAT catalog", () -> rest(server).get()
        .uri(DCAT).accept(TURTLE)
        .retrieve().body(String.class));
    return RdfDocument.turtle(turtle == null ? "" : turtle);
  }

  @Override
  public void createDcat(Instance server, DcatTarget target, RdfDocument dcat) {
    Path path = target instanceof ServerCatalog ? new Path(DCAT) : dcatPath(target);
    run(server, "create DCAT", () -> rest(server).post()
        .uri(path.template(), path.variables()).contentType(mediaType(dcat))
        .body(dcat.content())
        .retrieve().toBodilessEntity());
  }

  @Override
  public void updateDcat(Instance server, DcatTarget target, RdfDocument dcat) {
    Path path = dcatPath(target);
    run(server, "update DCAT", () -> rest(server).put()
        .uri(path.template(), path.variables()).contentType(mediaType(dcat))
        .body(dcat.content())
        .retrieve().toBodilessEntity());
  }

  @Override
  public void deleteDcat(Instance server, DcatTarget target) {
    Path path = dcatPath(target);
    run(server, "delete DCAT", () -> rest(server).delete()
        .uri(path.template(), path.variables())
        .retrieve().toBodilessEntity());
  }

  @Override
  public void ingest(Instance server, String collection, RdfDocument member) {
    run(server, "ingest member in " + collection, () -> rest(server).post()
        .uri("/{collection}", collection).contentType(mediaType(member))
        .body(member.content())
        .retrieve().toBodilessEntity());
  }

  @Override
  public List<StreamStatistics> getStatistics(Instance server) {
    String exposition = call(server, "get metrics", () -> rest(server).get()
        .uri("/actuator/prometheus").accept(MediaType.TEXT_PLAIN)
        .retrieve().body(String.class));
    return LdesStatisticsReader.read(PrometheusMetrics.parse(exposition == null ? ""
        : exposition));
  }

  private RestClient rest(Instance server) {
    return restClients.forInstance(server);
  }

  /**
   * Path of existing DCAT metadata.
   */
  private static Path dcatPath(DcatTarget target) {
    return switch (target) {
      case ServerCatalog catalog -> new Path(DCAT + "/{catalogId}", catalog.catalogId());
      case EventStreamDataset dataset -> new Path(EVENT_STREAM + "/dcat", dataset.collection());
      case ViewDataService service -> new Path(VIEW + "/dcat", service.collection(),
          service.view());
    };
  }

  private record Path(String template, Object... variables) {

  }

  private static MediaType mediaType(RdfDocument document) {
    return MediaType.valueOf(document.format().mediaType());
  }

  private static String base(Instance server) {
    return server.url() + "/";
  }

  private static <T> List<T> read(Instance server, String turtle,
      BiFunction<RdfDocument, String, List<T>> reader) {
    try {
      return reader.apply(RdfDocument.turtle(turtle == null ? "" : turtle), base(server));
    } catch (IllegalArgumentException e) {
      throw new RemoteInstanceException(Reason.UNAVAILABLE,
          "LDES server '%s' returned invalid RDF: %s".formatted(server.name(), e.getMessage()), e);
    }
  }

  private static <T> T single(Instance server, String what, List<T> items) {
    if (items.isEmpty()) {
      throw new RemoteInstanceException(Reason.NOT_FOUND,
          "LDES server '%s' did not return %s".formatted(server.name(), what));
    }
    return items.getFirst();
  }
}
