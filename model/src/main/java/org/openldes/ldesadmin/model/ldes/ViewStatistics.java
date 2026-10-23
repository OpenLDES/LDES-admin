package org.openldes.ldesadmin.model.ldes;

/**
 * Fragmentation progress of a view.
 *
 * @param bucketisedMembers members assigned to a bucket (fragment) of the view
 * @param paginatedMembers  members that ended up on a page of the view
 */
public record ViewStatistics(String view, long bucketisedMembers, long paginatedMembers) {

}
