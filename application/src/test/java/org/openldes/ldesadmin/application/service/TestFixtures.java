package org.openldes.ldesadmin.application.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.openldes.ldesadmin.model.ldio.StatusChangeSource;

public final class TestFixtures {

  public static final Instant NOW = Instant.parse("2026-10-23T10:15:30Z");
  public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private TestFixtures() {
  }

  public static Instance ldesServer() {
    return Instance.register(InstanceType.LDES_SERVER, "LDES server",
        URI.create("http://ldes.example.org"), false, NOW);
  }

  public static Instance ldio() {
    return Instance.register(InstanceType.LDIO, "LDIO", URI.create("http://ldio.example.org"),
        false, NOW);
  }

  public static Instance localLdio() {
    return Instance.local(InstanceType.LDIO, "Sandbox LDIO", URI.create("http://localhost:32768"),
        URI.create("http://sandbox-ldio:8080"), "container-42", true, NOW);
  }

  public static Instance localLdesServer() {
    return Instance.local(InstanceType.LDES_SERVER, "Sandbox LDES server",
        URI.create("http://localhost:32769"), URI.create("http://sandbox-ldes-server:8080"),
        "container-43", true, NOW);
  }

  public static Pipeline pipeline(String name) {
    return new Pipeline(name, "Test pipeline",
        new InputDefinition("Ldio:HttpIn", null, ComponentDefinition.of("Ldio:RdfAdapter")),
        null, List.of(ComponentDefinition.of("Ldio:ConsoleOut")));
  }

  public static DeployedPipeline deployed(String name, PipelineStatus status) {
    return new DeployedPipeline(pipeline(name), status, StatusChangeSource.AUTO);
  }
}
