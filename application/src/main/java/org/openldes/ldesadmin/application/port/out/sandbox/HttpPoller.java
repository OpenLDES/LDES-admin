package org.openldes.ldesadmin.application.port.out.sandbox;

import org.openldes.ldesadmin.model.sandbox.Poll;
import org.openldes.ldesadmin.model.sandbox.PolledContent;

/**
 * Makes the request of an HTTP In Poller, once.
 */
public interface HttpPoller {

  /**
   * @throws PollException if the URL is unreachable, answers with an error, or answers without a
   *                       content type
   */
  PolledContent poll(Poll poll);
}
