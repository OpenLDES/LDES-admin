package org.openldes.ldesadmin.application.port.out.ldes;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.ldes.BackgroundProcess;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldes.Fragment;
import org.openldes.ldesadmin.model.ldes.StreamStatistics;
import org.openldes.ldesadmin.model.ldes.View;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Talks to the admin API, ingest and fetch endpoints, and metrics of an LDES server.
 *
 * <p>Implementations throw a {@link
 * org.openldes.ldesadmin.application.port.out.RemoteInstanceException} when the server cannot
 * fulfil a request.
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

  /** The {@code dcat:Catalog} of the server itself, or an empty one to start from. */
  CatalogDescription getServerCatalog(Instance server);

  /**
   * @return the id of the created catalog, when the server returns one
   */
  Optional<String> createDcat(Instance server, DcatTarget target, RdfDocument dcat);

  void updateDcat(Instance server, DcatTarget target, RdfDocument dcat);

  void deleteDcat(Instance server, DcatTarget target);

  void ingest(Instance server, String collection, RdfDocument member);

  /**
   * Fetches a fragment of a view.
   *
   * @param url where LDES-admin reaches the fragment
   * @param timestampPath property holding the timestamp of a member, may be {@code null}
   * @param versionOfPath property linking a member to its state object, may be {@code null}
   */
  Fragment getFragment(Instance server, URI url, String timestampPath, String versionOfPath);

  List<StreamStatistics> getStatistics(Instance server);

  /** The steps of the fragmentation and maintenance jobs, from the batch metrics. */
  List<BackgroundProcess> getBackgroundProcesses(Instance server);
}
