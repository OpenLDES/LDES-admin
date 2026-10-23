package org.openldes.ldesadmin.application.port.out.ldes;

import java.util.List;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldes.View;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Talks to the admin API, ingest endpoint and metrics of an LDES server.
 *
 * <p>Implementations throw a {@link org.openldes.ldesadmin.application.port.out.RemoteInstanceException}
 * when the server cannot fulfil a request.
 */
public interface LdesServerClient {

  List<EventStream> getEventStreams(Instance server);

  EventStream getEventStream(Instance server, String collection);

  EventStream createEventStream(Instance server, RdfDocument definition);

  void deleteEventStream(Instance server, String collection);

  List<View> getViews(Instance server, String collection);

  View getView(Instance server, String collection, String view);

  View createView(Instance server, String collection, RdfDocument definition);

  void deleteView(Instance server, String collection, String view);

  RdfDocument getShape(Instance server, String collection);

  RdfDocument updateShape(Instance server, String collection, RdfDocument shape);

  RdfDocument getCatalog(Instance server);

  void createDcat(Instance server, DcatTarget target, RdfDocument dcat);

  void updateDcat(Instance server, DcatTarget target, RdfDocument dcat);

  void deleteDcat(Instance server, DcatTarget target);

  void ingest(Instance server, String collection, RdfDocument member);

  List<StreamStatistics> getStatistics(Instance server);
}
