package org.openldes.ldesadmin.adapter.in.rest.ldes;

import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.JSON_LD;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_QUADS;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_TRIPLES;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.TURTLE;

import java.util.UUID;
import org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes;
import org.openldes.ldesadmin.application.port.in.ldes.DcatUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.CatalogDescription;
import org.openldes.ldesadmin.model.ldes.DcatTarget;
import org.openldes.ldesadmin.model.ldes.DcatTarget.EventStreamDataset;
import org.openldes.ldesadmin.model.ldes.DcatTarget.ServerCatalog;
import org.openldes.ldesadmin.model.ldes.DcatTarget.ViewDataService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * DCAT metadata of the server ({@code dcat:Catalog}), its event streams ({@code dcat:Dataset})
 * and their views ({@code dcat:DataService}).
 */
@RestController
@RequestMapping("/api/v1/ldes-servers/{serverId}")
class DcatController {

  private static final String STREAM_DCAT = "/eventstreams/{collection}/dcat";
  private static final String VIEW_DCAT = "/eventstreams/{collection}/views/{view}/dcat";

  private final DcatUseCase dcatUseCase;

  DcatController(DcatUseCase dcatUseCase) {
    this.dcatUseCase = dcatUseCase;
  }

  /**
   * The combined catalog: server catalog, datasets and data services.
   */
  @GetMapping(value = "/dcat", produces = TURTLE)
  String catalog(@PathVariable UUID serverId) {
    return dcatUseCase.getCatalog(new InstanceId(serverId)).content();
  }

  /**
   * The catalog of the server itself, ready to edit.
   */
  @GetMapping("/server-catalog")
  ServerCatalogWebModel serverCatalog(@PathVariable UUID serverId) {
    CatalogDescription catalog = dcatUseCase.getServerCatalog(new InstanceId(serverId));
    return new ServerCatalogWebModel(catalog.catalogId(), catalog.document().content());
  }

  /**
   * Creates or replaces the catalog of the server itself.
   */
  @PutMapping(value = "/server-catalog", consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  ServerCatalogWebModel saveServerCatalog(@PathVariable UUID serverId, @RequestBody String dcat,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    String catalogId = dcatUseCase.saveServerCatalog(new InstanceId(serverId),
        RdfMediaTypes.document(dcat, contentType));
    return new ServerCatalogWebModel(catalogId, dcat);
  }

  @PostMapping(value = "/dcat", consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.CREATED)
  void createServerCatalog(@PathVariable UUID serverId, @RequestBody String dcat,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    create(serverId, new ServerCatalog(null), dcat, contentType);
  }

  @PutMapping(value = "/dcat/{catalogId}", consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void updateServerCatalog(@PathVariable UUID serverId, @PathVariable String catalogId,
      @RequestBody String dcat, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    update(serverId, new ServerCatalog(catalogId), dcat, contentType);
  }

  @DeleteMapping("/dcat/{catalogId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteServerCatalog(@PathVariable UUID serverId, @PathVariable String catalogId) {
    dcatUseCase.delete(new InstanceId(serverId), new ServerCatalog(catalogId));
  }

  @PostMapping(value = STREAM_DCAT, consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.CREATED)
  void createDataset(@PathVariable UUID serverId, @PathVariable String collection,
      @RequestBody String dcat, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    create(serverId, new EventStreamDataset(collection), dcat, contentType);
  }

  @PutMapping(value = STREAM_DCAT, consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void updateDataset(@PathVariable UUID serverId, @PathVariable String collection,
      @RequestBody String dcat, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    update(serverId, new EventStreamDataset(collection), dcat, contentType);
  }

  @DeleteMapping(STREAM_DCAT)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteDataset(@PathVariable UUID serverId, @PathVariable String collection) {
    dcatUseCase.delete(new InstanceId(serverId), new EventStreamDataset(collection));
  }

  @PostMapping(value = VIEW_DCAT, consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.CREATED)
  void createDataService(@PathVariable UUID serverId, @PathVariable String collection,
      @PathVariable String view, @RequestBody String dcat,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    create(serverId, new ViewDataService(collection, view), dcat, contentType);
  }

  @PutMapping(value = VIEW_DCAT, consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void updateDataService(@PathVariable UUID serverId, @PathVariable String collection,
      @PathVariable String view, @RequestBody String dcat,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    update(serverId, new ViewDataService(collection, view), dcat, contentType);
  }

  @DeleteMapping(VIEW_DCAT)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteDataService(@PathVariable UUID serverId, @PathVariable String collection,
      @PathVariable String view) {
    dcatUseCase.delete(new InstanceId(serverId), new ViewDataService(collection, view));
  }

  /**
   * @param catalogId the id the server gave the catalog, {@code null} if it has none yet
   * @param document  the catalog in Turtle
   */
  record ServerCatalogWebModel(String catalogId, String document) {

  }

  private void create(UUID serverId, DcatTarget target, String dcat, String contentType) {
    dcatUseCase.create(new InstanceId(serverId), target,
        RdfMediaTypes.document(dcat, contentType));
  }

  private void update(UUID serverId, DcatTarget target, String dcat, String contentType) {
    dcatUseCase.update(new InstanceId(serverId), target,
        RdfMediaTypes.document(dcat, contentType));
  }
}
