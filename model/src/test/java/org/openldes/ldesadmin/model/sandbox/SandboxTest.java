package org.openldes.ldesadmin.model.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;

class SandboxTest {

  private static final Instant NOW = Instant.parse("2026-10-24T10:15:30Z");
  private static final Instance LDES =
      Instance.local(
          InstanceType.LDES_SERVER,
          Sandbox.LDES_SERVER_NAME,
          URI.create("http://localhost:32768"),
          URI.create("http://sandbox-ldes-server:8080"),
          "c1",
          true,
          NOW);
  private static final Instance LDIO =
      Instance.local(
          InstanceType.LDIO,
          Sandbox.LDIO_NAME,
          URI.create("http://localhost:32769"),
          URI.create("http://sandbox-ldio:8080"),
          "c2",
          true,
          NOW);

  @Test
  void runsWhenBothInstancesRun() {
    assertThat(new Sandbox(LDES, LDIO).state()).isEqualTo(Sandbox.State.RUNNING);
    assertThat(new Sandbox(LDES, null).state()).isEqualTo(Sandbox.State.PARTIAL);
    assertThat(Sandbox.stopped().state()).isEqualTo(Sandbox.State.STOPPED);
  }

  @Test
  void theLdioReachesTheEventStreamOnTheInternalUrl() {
    assertThat(new Sandbox(LDES, LDIO).eventStreamUrlForLdio())
        .contains(URI.create("http://sandbox-ldes-server:8080/sandbox-parcels"));
    assertThat(Sandbox.stopped().eventStreamUrlForLdio()).isEmpty();
  }
}
