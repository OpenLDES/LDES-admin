package org.openldes.ldesadmin.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.application.port.out.sandbox.PollException;
import org.openldes.ldesadmin.model.sandbox.Poll;
import org.openldes.ldesadmin.model.sandbox.Poll.ClientCredentials;
import org.openldes.ldesadmin.model.sandbox.PolledContent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestHttpPollerTest {

  private static final String URL = "https://api.example.org/changes?since=2026-10-26";
  private static final String TOKEN_ENDPOINT = "https://login.example.org/token";

  private MockRestServiceServer mockServer;
  private RestHttpPoller poller;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    poller = new RestHttpPoller(builder);
  }

  @Test
  void getsTheUrlWithTheHeadersOfTheComponent() {
    mockServer
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-API-KEY", "secret"))
        // Like LDIO: the server answers in the format it prefers
        .andExpect(headerDoesNotExist(HttpHeaders.ACCEPT))
        .andRespond(withSuccess("[{\"id\": 1}]", MediaType.APPLICATION_JSON));

    PolledContent content =
        poller.poll(new Poll(URI.create(URL), Map.of("X-API-KEY", "secret"), null));

    assertThat(content).isEqualTo(new PolledContent("application/json", "[{\"id\": 1}]"));
    mockServer.verify();
  }

  @Test
  void readsTheBodyInItsCharset() {
    MediaType latin1 = new MediaType("text", "csv", StandardCharsets.ISO_8859_1);
    mockServer
        .expect(requestTo(URL))
        .andRespond(withSuccess("name\nCafé".getBytes(StandardCharsets.ISO_8859_1), latin1));

    assertThat(poller.poll(Poll.of(URI.create(URL))))
        .isEqualTo(new PolledContent("text/csv;charset=ISO-8859-1", "name\nCafé"));
  }

  @Test
  void reportsAnErrorResponse() {
    mockServer
        .expect(requestTo(URL))
        .andRespond(withStatus(HttpStatus.NOT_FOUND).body("No such dataset"));

    assertThatThrownBy(() -> poller.poll(Poll.of(URI.create(URL))))
        .isInstanceOf(PollException.class)
        .hasMessage(URL + " answered 404 NOT_FOUND - No such dataset");
  }

  @Test
  void refusesAResponseWithoutContentType() {
    mockServer.expect(requestTo(URL)).andRespond(withSuccess("data", null));

    assertThatThrownBy(() -> poller.poll(Poll.of(URI.create(URL))))
        .isInstanceOf(PollException.class)
        .hasMessage(URL + " answered without a Content-Type, which LDIO needs to pass it on");
  }

  @Test
  void refusesAResponseTooLargeForTheSandbox() {
    mockServer
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(new byte[RestHttpPoller.MAX_BODY_SIZE + 1], MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> poller.poll(Poll.of(URI.create(URL))))
        .isInstanceOf(PollException.class)
        .hasMessage(URL + " answered more than 5 MB, too much for the sandbox");
  }

  @Test
  void reportsAnUnreachableUrl() {
    mockServer
        .expect(requestTo(URL))
        .andRespond(withException(new ConnectException("Connection refused")));

    assertThatThrownBy(() -> poller.poll(Poll.of(URI.create(URL))))
        .isInstanceOf(PollException.class)
        .hasMessage("Could not poll " + URL + ": it is unreachable");
  }

  @Test
  void getsAnAccessTokenFirstWithClientCredentials() {
    mockServer
        .expect(requestTo(TOKEN_ENDPOINT))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .formDataContains(
                    Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "ldes-admin",
                        "client_secret", "s3cr3t",
                        "scope", "read")))
        .andRespond(
            withSuccess(
                "{\"access_token\": \"t0k3n\", \"token_type\": \"Bearer\"}",
                MediaType.APPLICATION_JSON));
    mockServer
        .expect(requestTo(URL))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer t0k3n"))
        .andRespond(withSuccess("<a> <b> <c> .", MediaType.valueOf("text/turtle")));

    PolledContent content = poller.poll(new Poll(URI.create(URL), Map.of(), credentials()));

    assertThat(content.body()).isEqualTo("<a> <b> <c> .");
    mockServer.verify();
  }

  @Test
  void reportsThatNoAccessTokenWasGranted() {
    mockServer.expect(requestTo(TOKEN_ENDPOINT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> poller.poll(new Poll(URI.create(URL), Map.of(), credentials())))
        .isInstanceOf(PollException.class)
        .hasMessage("Could not get an access token from " + TOKEN_ENDPOINT + ": 401 UNAUTHORIZED");
  }

  private static ClientCredentials credentials() {
    return new ClientCredentials(URI.create(TOKEN_ENDPOINT), "ldes-admin", "s3cr3t", "read");
  }
}
