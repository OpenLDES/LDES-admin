package org.openldes.ldesadmin.adapter.out.sandbox;

import io.micrometer.observation.ObservationRegistry;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.LdioLdesClientConnectorApi;
import org.openldes.ldio.config.LdioAmqpInAutoConfig;
import org.openldes.ldio.config.LdioAmqpInRegistrator;
import org.openldes.ldio.config.LdioHttpInAutoConfig;
import org.openldes.ldio.config.LdioHttpInputPollerAutoConfig;
import org.openldes.ldio.config.LdioKafkaInAutoConfig;
import org.openldes.ldio.config.LdioLdesClientAutoConfig;
import org.openldes.ldio.config.LdioLdesClientConnectorAutoConfig;
import org.openldes.ldio.event.LdesClientConnectorApiCreatedEvent;
import org.openldes.ldio.management.status.ClientStatusService;
import org.openldes.ldio.pipeline.creation.LdioInput;
import org.openldes.ldio.pipeline.creation.LdioInputConfigurator;
import org.openldes.ldio.pipeline.creation.events.PipelineShutdownEvent;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.GenericApplicationContext;

/** Runs only a source, never the admin application, in a disposable JVM. */
public final class LdioSourceWorker {
  private LdioSourceWorker() {}

  public static void main(String[] args) {
    DataOutputStream outbound = new DataOutputStream(System.out);
    System.setOut(System.err);
    ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
        .detachAndStopAllAppenders();
    DataInputStream inbound = new DataInputStream(System.in);
    try (GenericApplicationContext context = new GenericApplicationContext();
        LdioFailureListener failures =
            new LdioFailureListener(
                Logger.ROOT_LOGGER_NAME,
                false,
                error -> emit(outbound, new SourceWire.Message("error", null, error)))) {
      context.refresh();
      InputDefinition definition = SourceWire.read(inbound, InputDefinition.class);
      BlockingQueue<RunInput> commands = new ArrayBlockingQueue<>(16);
      AtomicReference<LdioLdesClientConnectorApi> connector = new AtomicReference<>();
      Thread.ofVirtual()
          .name("sandbox-source-commands")
          .start(() -> readCommands(inbound, outbound, commands, connector));
      ComponentProperties properties =
          LdioComponents.properties(
              LdioConfig.of(definition.name(), definition.config()),
              "sandbox-" + UUID.randomUUID());
      // Source state is disposable and must not read or write arbitrary host locations.
      properties.getConfig().put("state", "MEMORY");
      properties.getConfig().put("keepstate", "false");
      properties.getConfig().putIfAbsent("orchestrator.name", "ldes-admin-sandbox");
      properties.getConfig().put("continueonfail", "false");
      LdiAdapter capture =
          content -> {
            emitInput(outbound, RunInput.of(content.content(), content.mimeType()));
            return Stream.empty();
          };
      ComponentExecutor executor =
          model -> emitInput(outbound, RunInput.of(Rdf.toTurtle(model), "text/turtle"));
      ApplicationEventPublisher events =
          event -> {
            if (event instanceof LdesClientConnectorApiCreatedEvent created) {
              connector.set(created.ldesClientConnectorApi());
              emit(outbound, new SourceWire.Message("ready", null, null));
            } else if (event instanceof PipelineShutdownEvent) {
              emit(outbound, new SourceWire.Message("complete", null, null));
            }
          };
      LdioInput input =
          configurator(definition.name(), context, events)
              .configure(capture, executor, events, properties);
      emit(outbound, new SourceWire.Message("ready", null, null));
      while (true) {
        RunInput message = commands.take();
        if (!definition.name().equals("Ldio:HttpIn")) {
          throw new IllegalArgumentException("Only a live HTTP In accepts manually sent members");
        }
        input.processInput(message.content(), message.contentType());
      }
    } catch (IOException | RuntimeException e) {
      emit(outbound, new SourceWire.Message("error", null, e.getMessage()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      emit(outbound, new SourceWire.Message("error", null, "The live source was interrupted"));
    } finally {
      // This entry point runs only in the worker: leaked upstream connector threads die with it.
      System.exit(0);
    }
  }

  private static LdioInputConfigurator configurator(
      String name, GenericApplicationContext context, ApplicationEventPublisher events) {
    ObservationRegistry observations = ObservationRegistry.NOOP;
    return switch (name) {
      case "Ldio:HttpIn" -> new LdioHttpInAutoConfig().ldioConfigurator(events, observations);
      case "Ldio:HttpInPoller" ->
          new LdioHttpInputPollerAutoConfig().httpInputPollerConfigurator(observations);
      case "Ldio:AmqpIn" ->
          new LdioAmqpInAutoConfig()
              .ldioConfigurator(new LdioAmqpInRegistrator(context), observations);
      case "Ldio:KafkaIn" -> new LdioKafkaInAutoConfig().ldioConfigurator(observations);
      case "Ldio:LdesClient" ->
          new LdioLdesClientAutoConfig().ldioConfigurator(new ClientStatusService(), observations);
      case "Ldio:LdesClientConnector" ->
          new LdioLdesClientConnectorAutoConfig()
              .ldioConfigurator(events, new ClientStatusService(), observations);
      default -> throw new IllegalArgumentException(name + " is not a supported live input");
    };
  }

  private static void readCommands(
      DataInputStream inbound,
      DataOutputStream outbound,
      BlockingQueue<RunInput> commands,
      AtomicReference<LdioLdesClientConnectorApi> connector) {
    try {
      while (true) {
        SourceWire.Command command = SourceWire.read(inbound, SourceWire.Command.class);
        if (command.type().equals("input")) {
          if (!commands.offer(command.input())) {
            throw new IllegalArgumentException(
                "The live source exceeded the 16-member sandbox queue");
          }
        } else {
          connectorCommand(outbound, connector.get(), command);
        }
      }
    } catch (EOFException e) {
      // Losing the parent must not leave a polling/broker JVM behind.
      System.exit(0);
    } catch (IOException | RuntimeException e) {
      emit(outbound, new SourceWire.Message("error", null, e.getMessage()));
      System.exit(1);
    }
  }

  private static void emitInput(DataOutputStream outbound, RunInput input) {
    if (input.content().getBytes(StandardCharsets.UTF_8).length > SourceWire.MAX_CONTENT_BYTES) {
      throw new IllegalArgumentException("The live source member exceeds 5 MB");
    }
    emit(outbound, new SourceWire.Message("input", input, null));
  }

  private static void connectorCommand(
      DataOutputStream outbound, LdioLdesClientConnectorApi connector, SourceWire.Command command) {
    try {
      if (connector == null) {
        throw new IllegalArgumentException("The live LDES connector is not configured");
      }
      String answer =
          switch (command.type()) {
            case "token" -> {
              connector.handleToken(command.body());
              yield "";
            }
            case "transfer" -> connector.handleTransfer(command.body());
            default -> throw new IllegalArgumentException("Unknown live connector callback");
          };
      emit(outbound, new SourceWire.Message("reply", null, null, command.id(), answer));
    } catch (RuntimeException e) {
      emit(outbound, new SourceWire.Message("reply", null, e.getMessage(), command.id(), null));
    }
  }

  private static void emit(DataOutputStream stream, SourceWire.Message message) {
    try {
      SourceWire.write(stream, message);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
