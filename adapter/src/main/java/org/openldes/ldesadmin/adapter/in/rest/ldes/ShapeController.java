package org.openldes.ldesadmin.adapter.in.rest.ldes;

import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.JSON_LD;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_QUADS;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_TRIPLES;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.TURTLE;

import java.util.UUID;
import org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes;
import org.openldes.ldesadmin.application.port.in.ldes.ShapeUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ldes-servers/{serverId}/eventstreams/{collection}/shape")
class ShapeController {

  private final ShapeUseCase shapeUseCase;

  ShapeController(ShapeUseCase shapeUseCase) {
    this.shapeUseCase = shapeUseCase;
  }

  @GetMapping(produces = TURTLE)
  String get(@PathVariable UUID serverId, @PathVariable String collection) {
    return shapeUseCase.get(new InstanceId(serverId), collection).content();
  }

  @PutMapping(
      consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES},
      produces = TURTLE)
  String update(
      @PathVariable UUID serverId,
      @PathVariable String collection,
      @RequestBody String shape,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    return shapeUseCase
        .update(new InstanceId(serverId), collection, RdfMediaTypes.document(shape, contentType))
        .content();
  }
}
