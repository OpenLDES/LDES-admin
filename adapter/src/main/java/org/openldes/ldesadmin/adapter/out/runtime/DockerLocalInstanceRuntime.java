package org.openldes.ldesadmin.adapter.out.runtime;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
 * <p>Local instances are meant for experimenting: Testcontainers removes them when LDES-admin
 * stops. Their registrations remain, and show up as down until they are unregistered.
 */
@Component
public class DockerLocalInstanceRuntime implements LocalInstanceRuntime {

  private static final Logger log = LoggerFactory.getLogger(DockerLocalInstanceRuntime.class);
  private static final int HTTP_PORT = 8080;
  private static final String POSTGRES_ALIAS = "ldes-postgres";

  private final LocalRuntimeProperties properties;
  private final Map<String, LocalDeployment> deployments = new ConcurrentHashMap<>();

  public DockerLocalInstanceRuntime(LocalRuntimeProperties properties) {
    this.properties = properties;
  }

  @Override
  public LocalRuntimeHandle start(InstanceType type, String name) {
    log.info("Starting local {} '{}'", type, name);
    LocalDeployment deployment;
    try {
      deployment = switch (type) {
        case LDES_SERVER -> startLdesServer();
        case LDIO -> startLdio();
      };
    } catch (RuntimeException e) {
      throw new LocalRuntimeException("Could not start a local %s: %s".formatted(type,
          e.getMessage()), e);
    }
    deployments.put(deployment.reference(), deployment);
    return new LocalRuntimeHandle(deployment.url(), deployment.reference());
  }

  @Override
  public void stop(String runtimeReference) {
    LocalDeployment deployment = deployments.remove(runtimeReference);
    if (deployment != null) {
      log.info("Stopping local instance {}", runtimeReference);
      deployment.stop();
    }
  }

  @PreDestroy
  void stopAll() {
    deployments.keySet().forEach(this::stop);
  }

  private LocalDeployment startLdesServer() {
    Network network = Network.newNetwork();
    PostgreSQLContainer postgres = new PostgreSQLContainer(properties.postgresImage())
        .withNetwork(network)
        .withNetworkAliases(POSTGRES_ALIAS);
    GenericContainer<?> server = new GenericContainer<>(properties.ldesServerImage())
        .withNetwork(network)
        .withExposedPorts(HTTP_PORT)
        .withEnv("SPRING_DATASOURCE_URL",
            "jdbc:postgresql://%s:5432/%s".formatted(POSTGRES_ALIAS, postgres.getDatabaseName()))
        .withEnv("SPRING_DATASOURCE_USERNAME", postgres.getUsername())
        .withEnv("SPRING_DATASOURCE_PASSWORD", postgres.getPassword())
        .withEnv("SPRING_BATCH_JDBC_INITIALIZESCHEMA", "always")
        // The mapped port is only known after starting, so let the server use relative URLs
        .withEnv("LDESSERVER_HOSTNAME", "http://localhost")
        .withEnv("LDESSERVER_USERELATIVEURL", "true")
        .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,prometheus")
        .withEnv("MANAGEMENT_TRACING_ENABLED", "false")
        .dependsOn(postgres)
        .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
            .withStartupTimeout(properties.startupTimeout()));
    return start(server, List.of(postgres), network);
  }

  private LocalDeployment startLdio() {
    GenericContainer<?> ldio = new GenericContainer<>(properties.ldioImage())
        .withExposedPorts(HTTP_PORT)
        .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,prometheus")
        .waitingFor(Wait.forHttp("/admin/api/v1/pipeline").forStatusCode(200)
            .withStartupTimeout(properties.startupTimeout()));
    return start(ldio, List.of(), null);
  }

  private static LocalDeployment start(GenericContainer<?> main, List<Startable> dependencies,
      Network network) {
    try {
      main.start();
    } catch (RuntimeException e) {
      new LocalDeployment(null, null, main, dependencies, network).stop();
      throw e;
    }
    URI url = URI.create("http://%s:%d".formatted(main.getHost(), main.getMappedPort(HTTP_PORT)));
    return new LocalDeployment(main.getContainerId(), url, main, dependencies, network);
  }

  private record LocalDeployment(String reference, URI url, GenericContainer<?> main,
                                 List<Startable> dependencies, Network network) {

    void stop() {
      main.stop();
      dependencies.forEach(Startable::stop);
      if (network != null) {
        network.close();
      }
    }
  }
}
