package org.openldes.ldesadmin.adapter.out.sandbox;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.jena.rdf.model.Model;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldi.types.LdiOutput;
import org.openldes.ldio.LdioRepositorySink;

/** Keeps previews side-effect free; only explicitly executed outputs enter real LDIO code. */
final class ConfiguredOutput implements Output, AutoCloseable {
  private static final Set<String> DRY_ONLY =
      Set.of("Ldio:KafkaOut", "Ldio:AmqpOut", "Ldio:LdioRdbOut");
  private final LdioComponents components;
  private final String name;
  private final LdioConfig config;
  private final Output preview;
  private LdiOutput component;
  private boolean closed;

  ConfiguredOutput(LdioComponents components, String name, LdioConfig config) {
    this.components = components;
    this.name = name;
    this.config = config;
    this.preview = Output.create(name, config);
    if (DRY_ONLY.contains(name)) {
      component = components.output(name, config);
    }
  }

  @Override
  public synchronized OutputResult write(Model model, boolean execute) {
    if (closed) {
      throw new ComponentException("The sandbox component's session has ended");
    }
    if (!execute || DRY_ONLY.contains(name) || model.isEmpty()) {
      return preview.write(model, execute && DRY_ONLY.contains(name));
    }
    OutputResult shown = preview.write(model, false);
    if (component == null) {
      component = components.output(name, config);
      if (component instanceof LdioRepositorySink sink) {
        sink.start();
      }
    }
    if (component instanceof LdioRepositorySink) {
      AtomicReference<String> failure = new AtomicReference<>();
      try (LdioFailureListener failures =
          new LdioFailureListener(LdioRepositorySink.class, true, failure::set)) {
        component.accept(model);
      }
      if (failure.get() != null) {
        throw new ComponentException(failure.get());
      }
    } else {
      component.accept(model);
    }
    return new OutputResult(
        shown.serialisation(),
        shown.contentType(),
        switch (name) {
          case "Ldio:HttpOut" -> "Sent the model to " + config.property("endpoint");
          case "Ldio:HttpSparqlOut" -> "Updated " + config.property("endpoint");
          case "Ldio:RepositorySink" ->
              "Added the model to repository %s of %s"
                  .formatted(config.property("repository-id"), config.property("sparql-host"));
          default -> shown.summary();
        });
  }

  @Override
  public synchronized void close() {
    if (!closed && component instanceof LdioRepositorySink sink) {
      sink.shutdown();
    }
    if (!closed && component != null) {
      components.close(component);
    }
    closed = true;
  }
}
