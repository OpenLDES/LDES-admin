package org.openldes.ldesadmin.application.port.in.ldes;

import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * Manages the Data Catalog Vocabulary (DCAT) metadata of an LDES server, its event streams and
 * their views.
 *
 * @see EventStreamUseCase for the exceptions thrown
 */
public interface DcatUseCase {

  /**
   * The combined DCAT catalog of the server: the server catalog with all datasets and data
   * services.
   */
  RdfDocument getCatalog(InstanceId serverId);

  /**
   * The {@code dcat:Catalog} of the server itself, or an empty one to start from.
   */
  CatalogDescription getServerCatalog(InstanceId serverId);

  /**
   * Creates the catalog of the server, or replaces it if it has one.
   *
   * @return the id of the catalog
   */
  String saveServerCatalog(InstanceId serverId, RdfDocument catalog);

  void create(InstanceId serverId, DcatTarget target, RdfDocument dcat);

  void update(InstanceId serverId, DcatTarget target, RdfDocument dcat);

  void delete(InstanceId serverId, DcatTarget target);
}
