package org.openldes.ldesadmin.application.service.sandbox;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase.StartLocalInstanceCommand;
import org.openldes.ldesadmin.application.port.in.instance.UnregisterInstanceUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.EventStreamUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.ViewUseCase;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineUseCase;
import org.openldes.ldesadmin.application.port.in.sandbox.SandboxUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.service.ApplicationService;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.sandbox.Sandbox;

@ApplicationService
public class SandboxService implements SandboxUseCase {

  private final InstanceRepository instanceRepository;
  private final StartLocalInstanceUseCase startLocalInstance;
  private final UnregisterInstanceUseCase unregisterInstance;
  private final EventStreamUseCase eventStreams;
  private final ViewUseCase views;
  private final PipelineUseCase pipelines;

  public SandboxService(InstanceRepository instanceRepository,
      StartLocalInstanceUseCase startLocalInstance, UnregisterInstanceUseCase unregisterInstance,
      EventStreamUseCase eventStreams, ViewUseCase views, PipelineUseCase pipelines) {
    this.instanceRepository = instanceRepository;
    this.startLocalInstance = startLocalInstance;
    this.unregisterInstance = unregisterInstance;
    this.eventStreams = eventStreams;
    this.views = views;
    this.pipelines = pipelines;
  }

  @Override
  public Sandbox get() {
    return new Sandbox(
        find(InstanceType.LDES_SERVER, Sandbox.LDES_SERVER_NAME).orElse(null),
        find(InstanceType.LDIO, Sandbox.LDIO_NAME).orElse(null));
  }

  @Override
  public Sandbox start() {
    Sandbox current = get();
    Future<Instance> server;
    Future<Instance> ldio;
    // Both take a while to start, so start them side by side
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      server = executor.submit(running(current.ldesServer(), InstanceType.LDES_SERVER,
          Sandbox.LDES_SERVER_NAME));
      ldio = executor.submit(running(current.ldio(), InstanceType.LDIO, Sandbox.LDIO_NAME));
    }
    Sandbox sandbox = new Sandbox(result(server), result(ldio));
    prepareEventStream(sandbox.ldesServer());
    prepareIngestPipeline(sandbox);
    return sandbox;
  }

  @Override
  public void stop() {
    Sandbox current = get();
    List<Future<?>> stopped;
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      stopped = Stream.of(current.ldesServer(), current.ldio())
          .filter(Objects::nonNull)
          .<Future<?>>map(instance -> executor.submit(
              () -> unregisterInstance.unregister(instance.type(), instance.id())))
          .toList();
    }
    stopped.forEach(SandboxService::result);
  }

  private Optional<Instance> find(InstanceType type, String name) {
    return instanceRepository.findByType(type).stream()
        .filter(instance -> instance.isLocal() && instance.sandbox())
        .filter(instance -> instance.name().equals(name))
        .findFirst();
  }

  private Callable<Instance> running(Instance instance, InstanceType type, String name) {
    return instance != null
        ? () -> instance
        : () -> startLocalInstance.start(new StartLocalInstanceCommand(type, name, true));
  }

  /**
   * Creates the event stream with its views, unless it exists already: then it is left as the
   * user shaped it.
   */
  private void prepareEventStream(Instance server) {
    boolean exists = eventStreams.list(server.id()).stream()
        .map(EventStream::collection)
        .anyMatch(Sandbox.EVENT_STREAM::equals);
    if (exists) {
      return;
    }
    eventStreams.create(server.id(), SandboxDefinitions.eventStream(server));
    SandboxDefinitions.VIEWS.keySet().stream().sorted().forEach(view ->
        views.create(server.id(), Sandbox.EVENT_STREAM, SandboxDefinitions.view(server, view)));
  }

  /**
   * Deploys the ingest pipeline, or redeploys it when it feeds another LDES server than the
   * sandbox one, e.g. one that was started before the current one.
   */
  private void prepareIngestPipeline(Sandbox sandbox) {
    URI eventStream = sandbox.eventStreamUrlForLdio().orElseThrow();
    Optional<DeployedPipeline> deployed = pipelines.list(sandbox.ldio().id()).stream()
        .filter(pipeline -> pipeline.name().equals(Sandbox.INGEST_PIPELINE))
        .findFirst();
    if (deployed.isEmpty()) {
      pipelines.create(sandbox.ldio().id(), SandboxDefinitions.ingestPipeline(eventStream));
    } else if (!SandboxDefinitions.feeds(deployed.get().pipeline(), eventStream)) {
      pipelines.update(sandbox.ldio().id(), SandboxDefinitions.ingestPipeline(eventStream));
    }
  }

  private static <T> T result(Future<T> future) {
    try {
      return future.get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      throw new IllegalStateException(e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the sandbox", e);
    }
  }
}
