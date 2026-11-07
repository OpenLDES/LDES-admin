package org.openldes.ldesadmin.adapter.in.rest.ldes;

import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.JSON_LD;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_QUADS;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.N_TRIPLES;
import static org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes.TURTLE;

import java.util.List;
import java.util.UUID;
import org.openldes.ldesadmin.adapter.in.rest.RdfMediaTypes;
import org.openldes.ldesadmin.application.port.in.ldes.BackgroundProcessUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.IngestMemberUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.StreamStatisticsUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldes.BackgroundProcess;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ingesting members and following how far the server got in processing them. */
@RestController
@RequestMapping("/api/v1/ldes-servers/{serverId}")
class MemberController {

  private final IngestMemberUseCase ingestMemberUseCase;
  private final StreamStatisticsUseCase streamStatisticsUseCase;
  private final BackgroundProcessUseCase backgroundProcessUseCase;

  MemberController(
      IngestMemberUseCase ingestMemberUseCase,
      StreamStatisticsUseCase streamStatisticsUseCase,
      BackgroundProcessUseCase backgroundProcessUseCase) {
    this.ingestMemberUseCase = ingestMemberUseCase;
    this.streamStatisticsUseCase = streamStatisticsUseCase;
    this.backgroundProcessUseCase = backgroundProcessUseCase;
  }

  /**
   * Ingests a member directly on an event stream. Fragmentation happens asynchronously on the
   * server.
   */
  @PostMapping(
      value = "/eventstreams/{collection}/members",
      consumes = {TURTLE, JSON_LD, N_QUADS, N_TRIPLES})
  @ResponseStatus(HttpStatus.ACCEPTED)
  void ingest(
      @PathVariable UUID serverId,
      @PathVariable String collection,
      @RequestBody String member,
      @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
    ingestMemberUseCase.ingest(
        new InstanceId(serverId), collection, RdfMediaTypes.document(member, contentType));
  }

  @GetMapping("/statistics")
  List<StreamStatisticsWebModel> statistics(@PathVariable UUID serverId) {
    return streamStatisticsUseCase.statistics(new InstanceId(serverId)).stream()
        .map(StreamStatisticsWebModel::fromDomain)
        .toList();
  }

  /** The steps of the jobs that fragment the members and maintain the event streams. */
  @GetMapping("/processes")
  List<BackgroundProcess> processes(@PathVariable UUID serverId) {
    return backgroundProcessUseCase.processes(new InstanceId(serverId));
  }
}
