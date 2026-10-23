package org.openldes.ldesadmin.adapter.out.health;

import java.time.Clock;
import java.time.Instant;
import org.openldes.ldesadmin.adapter.out.http.InstanceRestClients;
import org.openldes.ldesadmin.application.port.out.health.InstanceHealthProbe;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Asks the Spring Boot actuator of an instance for its health. Falls back to the admin API for
 * instances that don't expose {@code /actuator/health}.
 */
@Component
public class ActuatorHealthProbe implements InstanceHealthProbe {

  private final InstanceRestClients restClients;
  private final Clock clock;

  public ActuatorHealthProbe(InstanceRestClients restClients, Clock clock) {
    this.restClients = restClients;
    this.clock = clock;
  }

  @Override
  public InstanceHealth check(Instance instance) {
    Instant now = clock.instant();
    try {
      HttpStatus actuatorStatus = statusOf(instance, "/actuator/health");
      HttpStatus status = actuatorStatus == HttpStatus.NOT_FOUND
          ? statusOf(instance, adminEndpoint(instance))
          : actuatorStatus;
      return status.is2xxSuccessful() ? InstanceHealth.up(now) : InstanceHealth.down(now);
    } catch (RestClientException e) {
      return InstanceHealth.down(now);
    }
  }

  private HttpStatus statusOf(Instance instance, String path) {
    return restClients.forInstance(instance).get()
        .uri(path)
        .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
        .exchange((request, response) -> HttpStatus.valueOf(response.getStatusCode().value()));
  }

  private static String adminEndpoint(Instance instance) {
    return instance.type() == InstanceType.LDES_SERVER
        ? "/admin/api/v1/eventstreams"
        : "/admin/api/v1/pipeline";
  }
}
