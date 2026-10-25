package org.openldes.ldesadmin.model.ldes;

import java.util.Objects;
import java.util.Optional;
import org.openldes.ldesadmin.model.rdf.RdfDocument;

/**
 * The {@code dcat:Catalog} describing an LDES server itself, without the datasets and data
 * services the server adds for its event streams and views.
 *
 * @param catalogId the id the server gave the catalog, {@code null} if it has none yet
 * @param document  the catalog, ready to be edited and sent back
 */
public record CatalogDescription(String catalogId, RdfDocument document) {

  public CatalogDescription {
    Objects.requireNonNull(document, "'document' must not be null");
  }

  public Optional<String> optionalCatalogId() {
    return Optional.ofNullable(catalogId);
  }
}
