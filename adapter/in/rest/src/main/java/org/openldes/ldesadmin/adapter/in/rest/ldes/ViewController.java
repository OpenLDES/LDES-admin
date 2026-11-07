package org.openldes.ldesadmin.adapter.in.rest.ldes;

import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.JSON_LD;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_QUADS;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_TRIPLES;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.TURTLE;

import java.util.List;
import java.util.UUID;
import org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes;
import org.openldes.ldesadmin.application.port.in.ldes.ViewUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ldes-servers/{serverId}/eventstreams/{collection}/views")
class ViewController {

  private final ViewUseCase viewUseCase;

  ViewController(ViewUseCase viewUseCase) {
    this.viewUseCase = viewUseCase;
  }

  @GetMapping
  List<ViewWebModel> list(@PathVariable UUID serverId, @PathVariable String collection) {
    return viewUseCase.list(new InstanceId(serverId), collection).stream()
        .map(ViewWebModel::fromDomain)
        .toList();
  }

  @GetMapping("/{view}")
  ViewWebModel get(
      @PathVariable UUID serverId, @PathVariable String collection, @PathVariable String view) {
    return ViewWebModel.fromDomain(viewUseCase.get(new InstanceId(serverId), collection, view));
  }

  /** Adds a view from its {@code tree:viewDescription}. */
  @PostMapping(consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.CREATED)
  ViewWebModel create(
      @PathVariable UUID serverId,
      @PathVariable String collection,
      @RequestBody String definition,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    return ViewWebModel.fromDomain(
        viewUseCase.create(
            new InstanceId(serverId), collection, RdfMediaTypes.document(definition, contentType)));
  }

  @DeleteMapping("/{view}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @PathVariable UUID serverId, @PathVariable String collection, @PathVariable String view) {
    viewUseCase.delete(new InstanceId(serverId), collection, view);
  }
}
