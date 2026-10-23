package org.openldes.ldesadmin.adapter.out.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.adapter.AdapterFixtures;
import org.openldes.ldesadmin.adapter.out.http.InstanceRestClients;
import org.openldes.ldesadmin.model.instance.HealthStatus;
import org.openldes.ldesadmin.model.instance.InstanceHealth;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ActuatorHealthProbeTest {

  private MockRestServiceServer mockServer;
  private ActuatorHealthProbe probe;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    probe = new ActuatorHealthProbe(new InstanceRestClients(builder),
        Clock.fixed(AdapterFixtures.NOW, ZoneOffset.UTC));
  }

  @Test
  void isUpWhenTheActuatorSaysSo() {
    mockServer.expect(requestTo("http://ldes.example.org/actuator/health"))
        .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

    InstanceHealth health = probe.check(AdapterFixtures.ldesServer());

    assertThat(health).isEqualTo(InstanceHealth.up(AdapterFixtures.NOW));
  }

  @Test
  void isDownWhenTheActuatorReportsServiceUnavailable() {
    mockServer.expect(requestTo("http://ldes.example.org/actuator/health"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThat(probe.check(AdapterFixtures.ldesServer()).status()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void fallsBackToTheAdminApiWithoutActuator() {
    mockServer.expect(requestTo("http://ldio.example.org/actuator/health"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    mockServer.expect(requestTo("http://ldio.example.org/admin/api/v1/pipeline"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(probe.check(AdapterFixtures.ldio()).status()).isEqualTo(HealthStatus.UP);
  }

  @Test
  void isDownWhenUnreachable() {
    mockServer.expect(requestTo("http://ldio.example.org/actuator/health"))
        .andRespond(withException(new ConnectException("Connection refused")));

    assertThat(probe.check(AdapterFixtures.ldio()).status()).isEqualTo(HealthStatus.DOWN);
  }
}
