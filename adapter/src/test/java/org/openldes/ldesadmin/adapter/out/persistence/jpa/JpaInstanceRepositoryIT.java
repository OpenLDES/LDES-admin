package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent;
import org.openldes.ldesadmin.model.monitoring.ActivityEvent.Severity;
import org.openldes.ldesadmin.model.monitoring.IngestSample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs against Postgres with the Flyway schema; Hibernate only validates that the entities match
 * it.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({JpaInstanceRepository.class, JpaActivityLog.class, JpaIngestSampleRepository.class})
class JpaInstanceRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-10-23T10:15:30Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired
  private JpaInstanceRepository repository;
  @Autowired
  private InstanceJpaRepository jpaRepository;
  @Autowired
  private JpaActivityLog activityLog;
  @Autowired
  private JpaIngestSampleRepository ingestSamples;

  @Test
  void storesAndRestoresInstances() {
    Instance local = Instance.local(InstanceType.LDIO, "Sandbox LDIO",
        URI.create("http://localhost:32768"), URI.create("http://sandbox-ldio:8080"),
        "container-42", true, NOW);

    repository.save(local);

    assertThat(repository.findById(local.id())).contains(local);
  }

  @Test
  void findsInstancesOfATypeOrderedByName() {
    Instance zulu = register(InstanceType.LDES_SERVER, "Zulu", "http://zulu.example.org");
    Instance alpha = register(InstanceType.LDES_SERVER, "Alpha", "http://alpha.example.org");
    register(InstanceType.LDIO, "LDIO", "http://ldio.example.org");

    assertThat(repository.findByType(InstanceType.LDES_SERVER)).containsExactly(alpha, zulu);
  }

  @Test
  void knowsWhetherAUrlIsRegistered() {
    register(InstanceType.LDES_SERVER, "Production", "http://ldes.example.org");

    assertThat(repository.existsByTypeAndUrl(InstanceType.LDES_SERVER,
        URI.create("http://ldes.example.org"))).isTrue();
    assertThat(repository.existsByTypeAndUrl(InstanceType.LDIO,
        URI.create("http://ldes.example.org"))).isFalse();
  }

  @Test
  void theDatabaseRefusesDuplicateUrls() {
    register(InstanceType.LDES_SERVER, "Production", "http://ldes.example.org");
    Instance duplicate = Instance.register(InstanceType.LDES_SERVER, "Again",
        URI.create("http://ldes.example.org"), false, NOW);

    assertThatThrownBy(() -> {
      repository.save(duplicate);
      jpaRepository.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletesInstances() {
    Instance instance = register(InstanceType.LDIO, "LDIO", "http://ldio.example.org");

    repository.deleteById(instance.id());

    assertThat(repository.findById(instance.id())).isEmpty();
  }

  @Test
  void keepsTheActivityNewestFirst() {
    Instance ldio = register(InstanceType.LDIO, "LDIO", "http://ldio.example.org");
    ActivityEvent added = ActivityEvent.of(ldio, Severity.INFO, "LDIO added", null, NOW);
    ActivityEvent deployed = ActivityEvent.of(ldio, Severity.SUCCESS, "Pipeline parcels deployed",
        "x".repeat(3_000), NOW.plusSeconds(60));
    ActivityEvent halted = ActivityEvent.of(ldio, Severity.WARN, "Pipeline parcels halted", null,
        NOW.plusSeconds(120));
    activityLog.record(added);
    activityLog.record(halted);
    activityLog.record(deployed);

    List<ActivityEvent> recent = activityLog.findRecent(2);

    assertThat(recent).extracting(ActivityEvent::title)
        .containsExactly("Pipeline parcels halted", "Pipeline parcels deployed");
    assertThat(recent.getLast().detail()).hasSize(2_048);
  }

  @Test
  void keepsIngestSamplesUntilTheyArePruned() {
    Instance server = register(InstanceType.LDES_SERVER, "Production", "http://ldes.example.org");
    IngestSample old = new IngestSample(server.id(), NOW.minus(Duration.ofHours(50)), 10);
    IngestSample recent = new IngestSample(server.id(), NOW.minus(Duration.ofMinutes(5)), 20);
    ingestSamples.save(recent);
    ingestSamples.save(old);

    assertThat(ingestSamples.findSince(NOW.minus(Duration.ofDays(3))))
        .containsExactly(old, recent);

    ingestSamples.deleteBefore(NOW.minus(Duration.ofHours(48)));

    assertThat(ingestSamples.findSince(NOW.minus(Duration.ofDays(3)))).containsExactly(recent);
  }

  @Test
  void removesTheIngestSamplesOfARemovedServer() {
    Instance server = register(InstanceType.LDES_SERVER, "Production", "http://ldes.example.org");
    ingestSamples.save(new IngestSample(server.id(), NOW, 20));
    jpaRepository.flush();

    repository.deleteById(server.id());
    jpaRepository.flush();

    assertThat(ingestSamples.findSince(NOW.minus(Duration.ofDays(1)))).isEmpty();
  }

  private Instance register(InstanceType type, String name, String url) {
    Instance instance = Instance.register(type, name, URI.create(url), false, NOW);
    repository.save(instance);
    jpaRepository.flush();
    return instance;
  }
}
