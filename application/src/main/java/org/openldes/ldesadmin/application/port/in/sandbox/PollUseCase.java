package org.openldes.ldesadmin.application.port.in.sandbox;

import org.openldes.ldesadmin.application.port.out.sandbox.PollException;
import org.openldes.ldesadmin.model.sandbox.Poll;
import org.openldes.ldesadmin.model.sandbox.PolledContent;

/**
 * Polls a URL the way the HTTP In Poller of an LDIO pipeline does, so the sandbox can simulate the
 * pipeline with what the poller would receive.
 *
 * <p>Unlike fragments, which LDES-admin only fetches from the LDES server they belong to, this
 * fetches any http(s) URL its users ask for: the API must be secured wherever LDES-admin reaches
 * what its users should not.
 */
public interface PollUseCase {

  /**
   * @throws PollException if the URL is unreachable, answers with an error, or answers what LDIO
   *     would not pass on to a pipeline
   */
  PolledContent poll(Poll poll);
}
