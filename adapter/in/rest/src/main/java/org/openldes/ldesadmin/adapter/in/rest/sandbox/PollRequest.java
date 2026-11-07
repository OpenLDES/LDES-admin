package org.openldes.ldesadmin.adapter.in.rest.sandbox;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Map;
import org.openldes.ldesadmin.model.sandbox.Poll;

/**
 * A URL to poll, with what the HTTP In Poller sends along.
 *
 * @param headers optional, e.g. the header with the API key
 * @param clientCredentials optional: get an OAuth2 access token first
 */
public record PollRequest(
    @NotNull URI url, Map<String, String> headers, ClientCredentialsRequest clientCredentials) {

  /**
   * @param scope optional
   */
  public record ClientCredentialsRequest(
      URI tokenEndpoint, String clientId, String clientSecret, String scope) {}

  Poll toDomain() {
    return new Poll(
        url,
        headers,
        clientCredentials == null
            ? null
            : new Poll.ClientCredentials(
                clientCredentials.tokenEndpoint(),
                clientCredentials.clientId(),
                clientCredentials.clientSecret(),
                clientCredentials.scope()));
  }
}
