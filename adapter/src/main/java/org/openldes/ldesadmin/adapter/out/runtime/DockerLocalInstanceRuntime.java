package org.openldes.ldesadmin.adapter.out.runtime;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.openldes.ldesadmin.application.port.out.runtime.LocalInstanceRuntime;
import org.openldes.ldesadmin.application.port.out.runtime.LocalRuntimeException;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs local LDES servers and LDIOs as Docker containers, using the Testcontainers API.
 *
 * <p>All local instances join one Docker network, on a host name derived from their name, e.g.
 * {@code http://sandbox-ldes-server:8080}. LDES servers publish their IRIs on that host name, so
 * an LDIO can follow the links in the pages it fetches.
 *
 * <p>Local instances are meant for experimenting: Testcontainers removes them when LDES-admin
 * stops.
 */
@Component
public class DockerLocalInstanceRuntime implements LocalInstanceRuntime {

  private static final Logger log = LoggerFactory.getLogger(DockerLocalInstanceRuntime.class);
  private static final int HTTP_PORT = 8080;
  private static final int MAX_ALIAS_LENGTH = 50;

  private final LocalRuntimeProperties properties;
  private final Network network = Network.newNetwork();
  private final Map<String, LocalDeployment> deployments = new ConcurrentHashMap<>();
  private final Set<String> aliases = new HashSet<>();

  public DockerLocalInstanceRuntime(LocalRuntimeProperties properties) {
    this.properties = properties;
  }

  @Override
  public LocalRuntimeHandle start(InstanceType type, String name) {
    String alias = reserveAlias(name, type);
    log.info("Starting local {} '{}' as {}", type.label(), name, alias);
    LocalDeployment deployment;
    try {
      deployment = switch (type) {
        case LDES_SERVER -> startLdesServer(alias);
        case LDIO -> startLdio(alias);
      };
    } catch (RuntimeException e) {
      releaseAlias(alias);
      throw new LocalRuntimeException("Could not start a local %s: %s".formatted(type.label(),
          e.getMessage()), e);
    }
    deployments.put(deployment.reference(), deployment);
    return new LocalRuntimeHandle(deployment.url(), internalUrl(alias), deployment.reference());
  }

  @Override
  public void stop(String runtimeReference) {
    LocalDeployment deployment = deployments.remove(runtimeReference);
    if (deployment != null) {
      log.info("Stopping local instance {}", deployment.alias());
      deployment.stop();
      releaseAlias(deployment.alias());
    }
  }

  /**
   * Containers are tied to the LDES-admin run that started them: Testcontainers removes those of
   * earlier runs.
   */
  @Override
  public boolean isRunning(String runtimeReference) {
    LocalDeployment deployment = deployments.get(runtimeReference);
    return deployment != null && deployment.main().isRunning();
  }

  @PreDestroy
  void stopAll() {
    deployments.keySet().forEach(this::stop);
    network.close();
  }

  private LocalDeployment startLdesServer(String alias) {
    String postgresAlias = alias + "-postgres";
    PostgreSQLContainer postgres = new PostgreSQLContainer(properties.postgresImage())
        .withNetwork(network)
        .withNetworkAliases(postgresAlias);
    GenericContainer<?> server = new GenericContainer<>(properties.ldesServerImage())
        .withNetwork(network)
        .withNetworkAliases(alias)
        .withExposedPorts(HTTP_PORT)
        .withEnv("SPRING_DATASOURCE_URL",
            "jdbc:postgresql://%s:5432/%s".formatted(postgresAlias, postgres.getDatabaseName()))
        .withEnv("SPRING_DATASOURCE_USERNAME", postgres.getUsername())
        .withEnv("SPRING_DATASOURCE_PASSWORD", postgres.getPassword())
        .withEnv("SPRING_BATCH_JDBC_INITIALIZESCHEMA", "always")
        // LDES clients need absolute IRIs, on a host name the other local instances resolve
        .withEnv("LDESSERVER_HOSTNAME", internalUrl(alias).toString())
        .withEnv("LDESSERVER_FRAGMENTATIONCRON", properties.fragmentationCron())
        .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,prometheus")
        .withEnv("MANAGEMENT_TRACING_ENABLED", "false")
        .dependsOn(postgres)
        .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
            .withStartupTimeout(properties.startupTimeout()));
    return start(alias, server, List.of(postgres));
  }

  private LocalDeployment startLdio(String alias) {
    GenericContainer<?> ldio = new GenericContainer<>(properties.ldioImage())
        .withNetwork(network)
        .withNetworkAliases(alias)
        .withExposedPorts(HTTP_PORT)
        .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,prometheus")
        .waitingFor(Wait.forHttp("/admin/api/v1/pipeline").forStatusCode(200)
            .withStartupTimeout(properties.startupTimeout()));
    return start(alias, ldio, List.of());
  }

  private static LocalDeployment start(String alias, GenericContainer<?> main,
      List<Startable> dependencies) {
    try {
      main.start();
    } catch (RuntimeException e) {
      new LocalDeployment(null, alias, null, main, dependencies).stop();
      throw e;
    }
    URI url = URI.create("http://%s:%d".formatted(main.getHost(), main.getMappedPort(HTTP_PORT)));
    return new LocalDeployment(main.getContainerId(), alias, url, main, dependencies);
  }

  /**
   * Derives a host name from the name of the instance, e.g. {@code sandbox-ldes-server} for
   * "Sandbox LDES server", made unique among the running instances.
   */
  synchronized String reserveAlias(String name, InstanceType type) {
    String base = slug(name);
    if (base.isEmpty()) {
      base = slug(type.label());
    }
    String alias = base;
    for (int i = 2; aliases.contains(alias); i++) {
      alias = base + "-" + i;
    }
    aliases.add(alias);
    return alias;
  }

  private synchronized void releaseAlias(String alias) {
    aliases.remove(alias);
  }

  static String slug(String name) {
    String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    String slug = ascii.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");
    return slug.length() <= MAX_ALIAS_LENGTH ? slug
        : slug.substring(0, MAX_ALIAS_LENGTH).replaceAll("-+$", "");
  }

  private static URI internalUrl(String alias) {
    return URI.create("http://%s:%d".formatted(alias, HTTP_PORT));
  }

  private record LocalDeployment(String reference, String alias, URI url,
                                 GenericContainer<?> main, List<Startable> dependencies) {

    void stop() {
      main.stop();
      dependencies.forEach(Startable::stop);
    }
  }
}
