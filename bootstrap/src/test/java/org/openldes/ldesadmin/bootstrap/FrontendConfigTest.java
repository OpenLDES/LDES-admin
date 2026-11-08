package org.openldes.ldesadmin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FrontendConfigTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "sandbox",
        "ldes-servers/6f3c1c1e-3f0a-4d44-9d1c-2a8d0e7c9b11",
        "ldes-servers/6f3c1c1e/eventstreams/parcels"
      })
  void routesTheFrontendPagesToTheFrontend(String path) {
    assertThat(FrontendConfig.isClientRoute(path)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "favicon.ico",
        "assets/index-missing.js",
        "api/v1/unknown",
        "api/v1",
        "actuator/unknown"
      })
  void leavesFilesAndTheBackendAlone(String path) {
    assertThat(FrontendConfig.isClientRoute(path)).isFalse();
  }
}
