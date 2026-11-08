package org.openldes.ldesadmin.application.service.sandbox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.openldes.ldesadmin.application.port.in.sandbox.LivePipelineUseCase;
import org.openldes.ldesadmin.application.port.in.sandbox.RunPipelineUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentRuntime;
import org.openldes.ldesadmin.application.port.out.sandbox.LiveSourceRuntime;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.sandbox.LivePipelineRun;
import org.openldes.ldesadmin.model.sandbox.PipelineRun;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldesadmin.model.sandbox.RunOptions;

@ApplicationService
public class LivePipelineService implements LivePipelineUseCase {
  private static final int MAX_SOURCES = 8;
  private final Map<String, LiveSession> sessions = new HashMap<>();
  private final LiveSourceRuntime runtime;
  private final RunPipelineUseCase runner;
  private final ComponentRuntime components;
  private final InstanceRepository instances;

  public LivePipelineService(
      LiveSourceRuntime runtime,
      RunPipelineUseCase runner,
      ComponentRuntime components,
      InstanceRepository instances) {
    this.runtime = runtime;
    this.runner = runner;
    this.components = components;
    this.instances = instances;
  }

  @Override
  public synchronized LivePipelineRun start(Pipeline pipeline, RunOptions options) {
    stop(options.session());
    sessions.entrySet().removeIf(entry -> !entry.getValue().snapshot().active());
    if (sessions.size() >= MAX_SOURCES) {
      throw new IllegalArgumentException(
          "At most " + MAX_SOURCES + " live sandbox sources may run");
    }
    Pipeline reachable = RunPipelineService.reachable(pipeline, instances);
    RunOptions liveOptions = new RunOptions(UUID.randomUUID().toString(), options.executeOutputs());
    LiveSession session = new LiveSession(() -> components.release(liveOptions.session()));
    sessions.put(options.session(), session);
    try {
      session.source =
          runtime.start(
              reachable.input(),
              input -> {
                if (session.snapshot().active()) {
                  session.receive(runner.run(reachable, input, liveOptions));
                }
              },
              session::fail);
      return session.snapshot();
    } catch (RuntimeException e) {
      sessions.remove(options.session());
      throw e;
    }
  }

  @Override
  public synchronized LivePipelineRun get(String id) {
    return session(id).snapshot();
  }

  @Override
  public synchronized void send(String id, RunInput input) {
    LiveSession session = session(id);
    if (!session.snapshot().active()) {
      throw new IllegalArgumentException("The live sandbox source is stopped");
    }
    session.source.send(input);
  }

  @Override
  public synchronized void stop(String id) {
    LiveSession session = sessions.get(id);
    if (session != null) {
      session.stop();
    }
  }

  @Override
  public String connector(String id, String action, String content) {
    LiveSession session;
    synchronized (this) {
      session = session(id);
      if (!session.snapshot().active()) {
        throw new IllegalArgumentException("The live sandbox source is stopped");
      }
    }
    return session.source.connector(action, content);
  }

  private LiveSession session(String id) {
    LiveSession session = sessions.get(id);
    if (session == null) {
      throw new IllegalArgumentException("No live sandbox source for this session");
    }
    return session;
  }

  private static final class LiveSession {
    private volatile LiveSourceRuntime.Source source;
    private boolean active = true;
    private long received;
    private PipelineRun latest;
    private String error;
    private final Runnable release;

    LiveSession(Runnable release) {
      this.release = release;
    }

    synchronized void receive(PipelineRun run) {
      if (active) {
        received++;
        latest = run;
      }
    }

    void fail(String message) {
      synchronized (this) {
        active = false;
        error = message;
      }
      release.run();
    }

    void stop() {
      synchronized (this) {
        active = false;
      }
      if (source != null) {
        source.close();
      }
      release.run();
    }

    synchronized LivePipelineRun snapshot() {
      return new LivePipelineRun(active, received, latest, error);
    }
  }
}
