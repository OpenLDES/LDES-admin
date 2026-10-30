package org.openldes.ldesadmin.adapter;

import java.net.URI;
import java.time.Instant;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;

public final class AdapterFixtures {

  public static final Instant NOW = Instant.parse("2026-10-23T10:15:30Z");

  private AdapterFixtures() {}

  public static Instance ldesServer() {
    return Instance.register(
        InstanceType.LDES_SERVER, "Production", URI.create("http://ldes.example.org"), false, NOW);
  }

  public static Instance ldio() {
    return Instance.register(
        InstanceType.LDIO, "Ingest LDIO", URI.create("http://ldio.example.org"), false, NOW);
  }
}
