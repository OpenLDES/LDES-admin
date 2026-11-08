package org.openldes.ldesadmin.application.port.out.sandbox;

import java.util.function.Consumer;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.sandbox.RunInput;

public interface LiveSourceRuntime {
  /** The completion callback receives an error, or {@code null} on normal source exhaustion. */
  Source start(InputDefinition input, Consumer<RunInput> receiver, Consumer<String> failure);

  interface Source extends AutoCloseable {
    void send(RunInput input);

    String connector(String action, String content);

    @Override
    void close();
  }
}
