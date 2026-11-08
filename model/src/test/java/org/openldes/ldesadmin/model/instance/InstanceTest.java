package org.openldes.ldesadmin.model.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InstanceTest {

  private static final Instant NOW = Instant.parse("2026-10-23T10:15:30Z");

  @Test
  void registeredInstanceHasNoRuntimeReference() {
    Instance instance =
        Instance.register(
            InstanceType.LDES_SERVER,
            " Production ",
            URI.create("http://ldes.example.org:8080/"),
            false,
            NOW);

    assertThat(instance.origin()).isEqualTo(InstanceOrigin.REGISTERED);
    assertThat(instance.isLocal()).isFalse();
    assertThat(instance.runtimeReference()).isNull();
    assertThat(instance.name()).isEqualTo("Production");
    assertThat(instance.registeredAt()).isEqualTo(NOW);
  }

  @Test
  void localInstanceKeepsItsRuntimeReference() {
    Instance instance =
        Instance.local(
            InstanceType.LDIO,
            "sandbox",
            URI.create("http://localhost:32768"),
            URI.create("http://sandbox-ldio:8080/"),
            "container-1",
            true,
            NOW);

    assertThat(instance.isLocal()).isTrue();
    assertThat(instance.sandbox()).isTrue();
    assertThat(instance.runtimeReference()).isEqualTo("container-1");
    assertThat(instance.internalUrl()).hasToString("http://sandbox-ldio:8080");
  }

  @Test
  void translatesIrisPublishedOnTheInternalUrl() {
    Instance instance =
        Instance.local(
            InstanceType.LDES_SERVER,
            "sandbox",
            URI.create("http://localhost:32768"),
            URI.create("http://sandbox-ldes-server:8080"),
            "container-1",
            true,
            NOW);

    assertThat(instance.reachable("http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=2"))
        .hasToString("http://localhost:32768/parcels/by-page?pageNumber=2");
    assertThat(instance.reachable("http://localhost:32768/parcels"))
        .hasToString("http://localhost:32768/parcels");
  }

  @Test
  void onlyServesUrlsBelowItsBaseUrl() {
    Instance instance =
        Instance.register(
            InstanceType.LDES_SERVER,
            "ldes",
            URI.create("http://ldes.example.org/ldes"),
            false,
            NOW);

    assertThat(instance.serves(URI.create("http://ldes.example.org/ldes/parcels"))).isTrue();
    assertThat(instance.serves(URI.create("http://ldes.example.org/ldes"))).isTrue();
    assertThat(instance.serves(URI.create("http://ldes.example.org/ldes-other/parcels"))).isFalse();
    assertThat(instance.serves(URI.create("http://evil.example.org/ldes/parcels"))).isFalse();
  }

  @Test
  void urlIsStoredWithoutTrailingSlash() {
    Instance instance =
        Instance.register(
            InstanceType.LDES_SERVER,
            "ldes",
            URI.create("http://localhost:8080/ldes//"),
            false,
            NOW);

    assertThat(instance.url()).hasToString("http://localhost:8080/ldes");
  }

  @Test
  void resolvesPathsAgainstTheBaseUrl() {
    Instance instance =
        Instance.register(
            InstanceType.LDES_SERVER, "ldes", URI.create("http://localhost:8080/ldes"), false, NOW);

    assertThat(instance.resolve("/admin/api/v1/eventstreams"))
        .hasToString("http://localhost:8080/ldes/admin/api/v1/eventstreams");
    assertThat(instance.resolve("parcels")).hasToString("http://localhost:8080/ldes/parcels");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://localhost/ldes", "localhost:8080", "/relative/path"})
  void rejectsUrlsThatAreNotAbsoluteHttpUrls(String url) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Instance.register(InstanceType.LDIO, "ldio", URI.create(url), false, NOW))
        .withMessageContaining("absolute http(s) URL");
  }

  @Test
  void rejectsBlankNames() {
    URI url = URI.create("http://localhost:8080");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> Instance.register(InstanceType.LDIO, "  ", url, false, NOW));
  }

  @Test
  void localInstanceRequiresARuntimeReference() {
    URI url = URI.create("http://localhost:8080");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> Instance.local(InstanceType.LDIO, "ldio", url, null, null, false, NOW));
  }
}
