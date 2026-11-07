package org.openldes.ldesadmin.adapter.out.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;
import org.openldes.ldesadmin.application.port.out.sandbox.LiveSourceRuntime;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.springframework.jms.core.JmsTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Real brokers, never a developer's configured Kafka consumer group or AMQP queue. */
class LdioBrokerSourcesIT {
  private static final String MEMBER = "<http://example.org/a> <http://example.org/b> \"c\" .";

  @Test
  void readsKafkaThroughTheRealAutoConfiguredInput() throws Exception {
    try (ConfluentKafkaContainer broker =
        new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))) {
      broker.start();
      LdioSourceRuntime runtime = new LdioSourceRuntime(Duration.ofSeconds(30));
      BlockingQueue<RunInput> received = new LinkedBlockingQueue<>();
      BlockingQueue<String> failures = new LinkedBlockingQueue<>();
      try (LiveSourceRuntime.Source source =
          runtime.start(
              new InputDefinition(
                  "Ldio:KafkaIn",
                  Map.of(
                      "bootstrap-servers", broker.getBootstrapServers(),
                      "topics", "sandbox-members",
                      "auto-offset-reset", "earliest"),
                  ComponentDefinition.of("Ldio:RdfAdapter")),
              received::add,
              failures::add)) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", broker.getBootstrapServers());
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
          producer.send(new ProducerRecord<>("sandbox-members", MEMBER)).get(20, TimeUnit.SECONDS);
        }

        assertThat(received.poll(30, TimeUnit.SECONDS))
            .isNotNull()
            .extracting(RunInput::content)
            .isEqualTo(MEMBER);
        assertThat(failures).isEmpty();
      } finally {
        runtime.destroy();
      }
    }
  }

  @Test
  void readsAmqpThroughTheRealAutoConfiguredInput() throws InterruptedException {
    try (GenericContainer<?> broker =
        new GenericContainer<>(DockerImageName.parse("apache/activemq-artemis:2.30.0-alpine"))
            .withEnv("ARTEMIS_USER", "sandbox")
            .withEnv("ARTEMIS_PASSWORD", "sandbox")
            .withExposedPorts(61616)) {
      broker.start();
      String endpoint = "amqp://" + broker.getHost() + ":" + broker.getMappedPort(61616);
      LdioSourceRuntime runtime = new LdioSourceRuntime(Duration.ofSeconds(30));
      BlockingQueue<RunInput> received = new LinkedBlockingQueue<>();
      BlockingQueue<String> failures = new LinkedBlockingQueue<>();
      try (LiveSourceRuntime.Source source =
          runtime.start(
              new InputDefinition(
                  "Ldio:AmqpIn",
                  Map.of(
                      "remote-url",
                      endpoint,
                      "username",
                      "sandbox",
                      "password",
                      "sandbox",
                      "queue",
                      "sandbox-members"),
                  ComponentDefinition.of("Ldio:RdfAdapter")),
              received::add,
              failures::add)) {
        JmsTemplate sender =
            new JmsTemplate(new JmsConnectionFactory("sandbox", "sandbox", endpoint));
        sender.convertAndSend("sandbox-members", MEMBER);

        assertThat(received.poll(30, TimeUnit.SECONDS))
            .isNotNull()
            .extracting(RunInput::content)
            .isEqualTo(MEMBER);
        assertThat(failures).isEmpty();
      } finally {
        runtime.destroy();
      }
    }
  }
}
