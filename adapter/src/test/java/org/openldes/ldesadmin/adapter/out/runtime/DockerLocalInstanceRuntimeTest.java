package org.openldes.ldesadmin.adapter.out.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.model.instance.InstanceType;

class DockerLocalInstanceRuntimeTest {

  private final DockerLocalInstanceRuntime runtime = new DockerLocalInstanceRuntime(
      new LocalRuntimeProperties("ldes-server", "ldio", "postgres", Duration.ofMinutes(1),
          "*/5 * * * * *"));

  @Test
  void derivesTheHostNameFromTheName() {
    assertThat(runtime.reserveAlias("Sandbox LDES server", InstanceType.LDES_SERVER))
        .isEqualTo("sandbox-ldes-server");
    assertThat(runtime.reserveAlias("  Ingest (Brussel) — percelen ", InstanceType.LDIO))
        .isEqualTo("ingest-brussel-percelen");
    assertThat(runtime.reserveAlias("Événement", InstanceType.LDIO)).isEqualTo("evenement");
  }

  @Test
  void makesHostNamesUnique() {
    assertThat(runtime.reserveAlias("Sandbox LDIO", InstanceType.LDIO)).isEqualTo("sandbox-ldio");
    assertThat(runtime.reserveAlias("Sandbox LDIO", InstanceType.LDIO))
        .isEqualTo("sandbox-ldio-2");
  }

  @Test
  void fallsBackOnTheTypeForNamesWithoutLettersOrDigits() {
    assertThat(runtime.reserveAlias("???", InstanceType.LDES_SERVER)).isEqualTo("ldes-server");
  }

  @Test
  void keepsHostNamesShort() {
    assertThat(DockerLocalInstanceRuntime.slug("a".repeat(49) + " b")).hasSize(49);
  }
}
