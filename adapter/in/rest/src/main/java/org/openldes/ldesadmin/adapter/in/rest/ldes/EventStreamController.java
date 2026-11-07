package org.openldes.ldesadmin.adapter.in.rest.ldes;

import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.JSON_LD;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_QUADS;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_TRIPLES;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.TURTLE;

import java.util.List;
import java.util.UUID;
import org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes;
import org.openldes.ldesadmin.application.port.in.ldes.EventStreamUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.EventStream;
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
@RequestMapping("/api/v1/ldes-servers/{serverId}/eventstreams")
class EventStreamController {

  private final EventStreamUseCase eventStreamUseCase;

  EventStreamController(EventStreamUseCase eventStreamUseCase) {
    this.eventStreamUseCase = eventStreamUseCase;
  }

  @GetMapping
  List<EventStreamWebModel> list(@PathVariable UUID serverId) {
    return eventStreamUseCase.list(new InstanceId(serverId)).stream()
        .map(EventStreamWebModel::fromDomain)
        .toList();
  }

  @GetMapping("/{collection}")
  EventStreamWebModel get(@PathVariable UUID serverId, @PathVariable String collection) {
    return EventStreamWebModel.fromDomain(
        eventStreamUseCase.get(new InstanceId(serverId), collection));
  }

  /**
   * Creates an event stream from its RDF definition, e.g. an {@code ldes:EventStream} in Turtle.
   */
  @PostMapping(consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.CREATED)
  EventStreamWebModel create(
      @PathVariable UUID serverId,
      @RequestBody String definition,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    EventStream created =
        eventStreamUseCase.create(
            new InstanceId(serverId), RdfMediaTypes.document(definition, contentType));
    return EventStreamWebModel.fromDomain(created);
  }

  @DeleteMapping("/{collection}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID serverId, @PathVariable String collection) {
    eventStreamUseCase.delete(new InstanceId(serverId), collection);
  }
}
