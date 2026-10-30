package org.openldes.ldesadmin.model.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.model.sandbox.Poll.ClientCredentials;

class PollTest {

  private static final URI URL = URI.create("https://api.example.org/changes/");

  @Test
  void keepsTheUrlAsItIs() {
    assertThat(Poll.of(URL).url()).hasToString("https://api.example.org/changes/");
    assertThat(Poll.of(URL).headers()).isEmpty();
  }

  @Test
  void onlyPollsHttpUrls() {
    assertThatThrownBy(() -> Poll.of(URI.create("ftp://api.example.org/changes")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("'url' must be an absolute http(s) URL, but was ftp://api.example.org/changes");
    assertThatThrownBy(() -> Poll.of(URI.create("/changes")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void needsANameAndValueForEveryHeader() {
    assertThatThrownBy(() -> new Poll(URL, Map.of(" ", "secret"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A header needs a name and a value");
  }

  @Test
  void needsCompleteClientCredentials() {
    assertThatThrownBy(
            () ->
                new ClientCredentials(
                    URI.create("https://login.example.org/token"), "ldes-admin", " ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("'clientSecret' must not be blank");
    assertThatThrownBy(() -> new ClientCredentials(null, "ldes-admin", "secret", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void keepsTheSecretOutOfTheDescription() {
    ClientCredentials credentials =
        new ClientCredentials(
            URI.create("https://login.example.org/token"), "ldes-admin", "s3cr3t", " ");

    assertThat(credentials.scope()).isNull();
    assertThat(credentials.toString()).contains("ldes-admin").doesNotContain("s3cr3t");
  }
}
