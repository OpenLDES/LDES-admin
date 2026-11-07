package org.openldes.ldesadmin.adapter.in.rest.ldes;

import java.util.List;
import org.openldes.ldesadmin.model.ldes.EventStream;

/**
 * @param definition the event stream in Turtle, as returned by the LDES server
 */
public record EventStreamWebModel(
    String collection,
    String timestampPath,
    String versionOfPath,
    boolean createVersions,
    List<String> views,
    String definition) {

  public static EventStreamWebModel fromDomain(EventStream stream) {
    return new EventStreamWebModel(
        stream.collection(),
        stream.timestampPath(),
        stream.versionOfPath(),
        stream.createVersions(),
        stream.views(),
        stream.definition().content());
  }
}
