package org.openldes.ldesadmin.model.ldes;

/**
 * The resource a piece of DCAT metadata describes on an LDES server.
 */
public sealed interface DcatTarget {

  /**
   * The {@code dcat:Catalog} of the server itself.
   *
   * @param catalogId id of an existing catalog; ignored when creating the catalog
   */
  record ServerCatalog(String catalogId) implements DcatTarget {

  }

  /**
   * The {@code dcat:Dataset} describing an event stream.
   */
  record EventStreamDataset(String collection) implements DcatTarget {

  }

  /**
   * The {@code dcat:DataService} describing a view of an event stream.
   */
  record ViewDataService(String collection, String view) implements DcatTarget {

  }
}
