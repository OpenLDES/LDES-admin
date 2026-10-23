package org.openldes.ldesadmin.model.ldes;

import java.util.List;

/**
 * How far an event stream got in processing its members.
 *
 * @param ingestedMembers number of members ingested in the event stream
 * @param views           progress of the fragmentation per view
 */
public record StreamStatistics(String collection, long ingestedMembers,
                               List<ViewStatistics> views) {

  public StreamStatistics {
    views = views == null ? List.of() : List.copyOf(views);
  }

  /**
   * Members that were assigned to a bucket but not yet put on a page, in the slowest view. Views
   * with a retention policy hold fewer members than were ingested, so the ingested count is not
   * a meaningful reference.
   */
  public long backlog() {
    return views.stream()
        .mapToLong(view -> Math.max(0, view.bucketisedMembers() - view.paginatedMembers()))
        .max()
        .orElse(0);
  }
}
