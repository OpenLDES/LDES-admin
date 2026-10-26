package org.openldes.ldesadmin.model.sandbox;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * The request the HTTP In Poller of an LDIO pipeline makes: a GET of a URL, with the headers and
 * credentials of the component. The sandbox makes it once, to run what the poller would receive
 * through the pipeline.
 *
 * @param url               absolute http(s) URL, used as it is (a trailing slash matters here)
 * @param headers           headers to send along, e.g. an API key; empty for none
 * @param clientCredentials to get an OAuth2 access token with first; {@code null} for none
 */
public record Poll(URI url, Map<String, String> headers, ClientCredentials clientCredentials) {

  public Poll {
    requireHttpUrl(url, "url");
    headers = headers == null ? Map.of() : headers;
    headers.forEach((name, value) -> {
      if (name == null || name.isBlank() || value == null) {
        throw new IllegalArgumentException("A header needs a name and a value");
      }
    });
    headers = Map.copyOf(headers);
  }

  public static Poll of(URI url) {
    return new Poll(url, Map.of(), null);
  }

  public Poll withUrl(URI otherUrl) {
    return new Poll(otherUrl, headers, clientCredentials);
  }

  /**
   * The OAuth2 client credentials grant as LDIO makes it: the client id and secret go in the body
   * of the token request.
   *
   * @param scope optional
   */
  public record ClientCredentials(URI tokenEndpoint, String clientId, String clientSecret,
                                  String scope) {

    public ClientCredentials {
      requireHttpUrl(tokenEndpoint, "tokenEndpoint");
      requireText(clientId, "clientId");
      requireText(clientSecret, "clientSecret");
      scope = scope == null || scope.isBlank() ? null : scope.strip();
    }

    /**
     * Leaves out the secret, should it end up in a log.
     */
    @Override
    public String toString() {
      return "ClientCredentials[tokenEndpoint=%s, clientId=%s, scope=%s]"
          .formatted(tokenEndpoint, clientId, scope);
    }
  }

  private static void requireHttpUrl(URI url, String name) {
    Objects.requireNonNull(url, "'%s' must not be null".formatted(name));
    String scheme = url.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)
        || url.getHost() == null) {
      throw new IllegalArgumentException(
          "'%s' must be an absolute http(s) URL, but was %s".formatted(name, url));
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("'%s' must not be blank".formatted(name));
    }
  }
}
