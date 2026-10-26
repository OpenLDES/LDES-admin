package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import org.openldes.ldesadmin.model.sandbox.PolledContent;

/**
 * @param contentType the Content-Type header as received, which LDIO hands to the adapter
 */
public record PolledContentWebModel(String contentType, String body) {

  public static PolledContentWebModel fromDomain(PolledContent content) {
    return new PolledContentWebModel(content.contentType(), content.body());
  }
}
