package org.openldes.ldesadmin.adapter.out.sandbox;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import org.apache.jena.rdf.model.Model;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentRuntime;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldi.types.LdiAdapter;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs the components of LDIO pipelines in LDES-admin, on the LDI libraries LDIO is built on.
 *
 * <p>Components are kept per session and position. A stateful one (the change detection filter)
 * thus remembers what it saw across the runs of a session, and a mapping or query is not parsed
 * again for every run. A component whose definition changed is made anew, as a redeployed
 * pipeline would. Sessions are forgotten after 30 minutes without a run, or when there are too
 * many.
 *
 * <p>Every call has a time limit ({@code ldes-admin.sandbox.component-timeout}): a query or an
 * HTTP request that hangs does not hold up the request of the user.
 */
@Component
public class LdiComponentRuntime implements ComponentRuntime, DisposableBean {

  static {
    // GeoSPARQL looks up coordinate systems in the embedded Derby database of Apache SIS, which
    // would otherwise write derby.log in the working directory of LDES-admin
    if (System.getProperty("derby.stream.error.file") == null) {
      System.setProperty("derby.stream.error.file",
          Path.of(System.getProperty("java.io.tmpdir"), "ldes-admin-derby.log").toString());
    }
  }

  private static final int MAX_SESSIONS = 100;
  private static final Duration SESSION_IDLE_TIMEOUT = Duration.ofMinutes(30);

  private final Duration componentTimeout;
  private final Clock clock;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  /** Least recently used first */
  private final Map<String, Session> sessions = new LinkedHashMap<>(16, 0.75f, true);

  public LdiComponentRuntime(
      @Value("${ldes-admin.sandbox.component-timeout:PT30S}") Duration componentTimeout,
      Clock clock) {
    this.componentTimeout = componentTimeout;
    this.clock = clock;
  }

  @Override
  public List<RdfDocument> read(RunInput input) {
    return call(() -> List.of(turtle(Rdf.parse(input.content(), input.contentType()))));
  }

  @Override
  public List<RdfDocument> adapt(String session, ComponentDefinition adapter, RunInput input) {
    return call(() -> {
      LdiAdapter component = component(session, "adapter", 0, adapter, Adapters::create);
      return component.apply(LdiAdapter.Content.of(input.content(), input.contentType()))
          .map(LdiComponentRuntime::turtle)
          .toList();
    });
  }

  @Override
  public List<RdfDocument> transform(String session, int position,
      ComponentDefinition transformer, RdfDocument model) {
    return call(() -> {
      Transformer component = component(session, "transformer", position, transformer,
          Transformer::create);
      return component.transform(Rdf.fromTurtle(model.content())).stream()
          .map(LdiComponentRuntime::turtle)
          .toList();
    });
  }

  @Override
  public OutputResult write(String session, int position, ComponentDefinition output,
      RdfDocument model, boolean execute) {
    return call(() -> component(session, "output", position, output, Output::create)
        .write(Rdf.fromTurtle(model.content()), execute));
  }

  @Override
  public void destroy() {
    executor.shutdownNow();
  }

  private <T> T component(String sessionId, String kind, int position,
      ComponentDefinition definition, BiFunction<String, LdioConfig, T> factory) {
    Session session = session(sessionId);
    synchronized (session) {
      String slot = kind + "/" + position;
      Kept kept = session.components.get(slot);
      if (kept == null || !kept.definition().equals(definition)) {
        kept = new Kept(definition, factory.apply(definition.name(),
            LdioConfig.of(definition.name(), definition.config())));
        session.components.put(slot, kept);
      }
      @SuppressWarnings("unchecked")
      T component = (T) kept.component();
      return component;
    }
  }

  private synchronized Session session(String id) {
    Instant now = clock.instant();
    Iterator<Session> least = sessions.values().iterator();
    while (least.hasNext()) {
      Session session = least.next();
      if (session.lastUsed.plus(SESSION_IDLE_TIMEOUT).isAfter(now)) {
        break;
      }
      least.remove();
    }
    Session session = sessions.computeIfAbsent(id, key -> new Session());
    session.lastUsed = now;
    if (sessions.size() > MAX_SESSIONS) {
      sessions.remove(sessions.keySet().iterator().next());
    }
    return session;
  }

  private <T> T call(Callable<T> work) {
    Future<T> future = executor.submit(work);
    try {
      return future.get(componentTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new ComponentException("Took longer than %s, the limit of the sandbox"
          .formatted(componentTimeout.toMillis() < 1000 ? componentTimeout.toMillis() + " ms"
              : componentTimeout.toSeconds() + " seconds"));
    } catch (ExecutionException e) {
      throw e.getCause() instanceof ComponentException failure ? failure
          : new ComponentException(messageOf(e.getCause()), e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ComponentException("Interrupted", e);
    }
  }

  /**
   * The first message along the causes: LDI and carml often wrap the error that explains it.
   */
  private static String messageOf(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
        return cause.getMessage();
      }
    }
    return error.getClass().getSimpleName();
  }

  private static RdfDocument turtle(Model model) {
    return RdfDocument.turtle(Rdf.toTurtle(model));
  }

  private static final class Session {

    private final Map<String, Kept> components = new HashMap<>();
    private Instant lastUsed;
  }

  private record Kept(ComponentDefinition definition, Object component) {

  }
}
