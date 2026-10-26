package org.openldes.ldesadmin.adapter.out.http;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.openldes.ldesadmin.application.port.out.sandbox.HttpPoller;
import org.openldes.ldesadmin.application.port.out.sandbox.PollException;
import org.openldes.ldesadmin.model.sandbox.Poll;
import org.openldes.ldesadmin.model.sandbox.Poll.ClientCredentials;
import org.openldes.ldesadmin.model.sandbox.PolledContent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Polls a URL like the HTTP In Poller of LDIO does: a GET without an Accept header, so the server
 * answers in its default format, with the headers of the component and, for OAuth2, a bearer
 * token. Uses the timeouts of the application ({@code spring.http.clients.*}).
 */
@Component
public class RestHttpPoller implements HttpPoller {

  /** More than the editor of the sandbox handles comfortably */
  static final int MAX_BODY_SIZE = 5 * 1024 * 1024;
  private static final int MAX_ERROR_LENGTH = 500;
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {
      };

  private final RestClient restClient;

  public RestHttpPoller(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.clone().build();
  }

  @Override
  public PolledContent poll(Poll poll) {
    String accessToken = poll.clientCredentials() == null
        ? null
        : accessToken(poll.clientCredentials());
    try {
      return restClient.get()
          .uri(poll.url())
          .headers(headers -> {
            poll.headers().forEach(headers::add);
            if (accessToken != null) {
              headers.setBearerAuth(accessToken);
            }
          })
          .exchange((request, response) -> read(poll.url(), response));
    } catch (ResourceAccessException e) {
      throw new PollException("Could not poll %s: it is unreachable".formatted(poll.url()), e);
    } catch (RestClientException e) {
      throw new PollException("Could not poll %s: %s".formatted(poll.url(), e.getMessage()), e);
    }
  }

  private static PolledContent read(URI url, ClientHttpResponse response) throws IOException {
    HttpStatusCode status = response.getStatusCode();
    if (!status.is2xxSuccessful()) {
      String body = new String(response.getBody().readNBytes(MAX_ERROR_LENGTH + 1),
          StandardCharsets.UTF_8).strip();
      if (body.length() > MAX_ERROR_LENGTH) {
        body = body.substring(0, MAX_ERROR_LENGTH) + "…";
      }
      throw new PollException("%s answered %s%s".formatted(url, status,
          body.isEmpty() ? "" : " - " + body));
    }
    String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
    if (contentType == null || contentType.isBlank()) {
      throw new PollException(
          "%s answered without a Content-Type, which LDIO needs to pass it on".formatted(url));
    }
    byte[] body = response.getBody().readNBytes(MAX_BODY_SIZE + 1);
    if (body.length > MAX_BODY_SIZE) {
      throw new PollException("%s answered more than %d MB, too much for the sandbox"
          .formatted(url, MAX_BODY_SIZE / 1024 / 1024));
    }
    return new PolledContent(contentType, new String(body, charsetOf(contentType)));
  }

  private static Charset charsetOf(String contentType) {
    try {
      Charset charset = MediaType.parseMediaType(contentType).getCharset();
      return charset == null ? StandardCharsets.UTF_8 : charset;
    } catch (IllegalArgumentException e) {
      // An invalid media type, or a charset Java does not know
      return StandardCharsets.UTF_8;
    }
  }

  /**
   * The client credentials grant as LDIO requests it: the client id and secret in the form, not
   * in an Authorization header.
   */
  private String accessToken(ClientCredentials credentials) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", credentials.clientId());
    form.add("client_secret", credentials.clientSecret());
    if (credentials.scope() != null) {
      form.add("scope", credentials.scope());
    }
    URI endpoint = credentials.tokenEndpoint();
    Map<String, Object> token;
    try {
      token = restClient.post()
          .uri(endpoint)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .accept(MediaType.APPLICATION_JSON)
          .body(form)
          .retrieve()
          .body(JSON_OBJECT);
    } catch (RestClientResponseException e) {
      throw new PollException("Could not get an access token from %s: %s"
          .formatted(endpoint, e.getStatusCode()), e);
    } catch (ResourceAccessException e) {
      throw new PollException(
          "Could not get an access token from %s: it is unreachable".formatted(endpoint), e);
    } catch (RestClientException e) {
      throw new PollException("Could not get an access token from %s: %s"
          .formatted(endpoint, e.getMessage()), e);
    }
    if (token == null || !(token.get("access_token") instanceof String accessToken)
        || accessToken.isBlank()) {
      throw new PollException("%s answered without an access token".formatted(endpoint));
    }
    return accessToken;
  }
}
