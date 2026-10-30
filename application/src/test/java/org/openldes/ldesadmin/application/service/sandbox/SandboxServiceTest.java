package org.openldes.ldesadmin.application.service.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase.StartLocalInstanceCommand;
import org.openldes.ldesadmin.application.port.in.instance.UnregisterInstanceUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.EventStreamUseCase;
import org.openldes.ldesadmin.application.port.in.ldes.ViewUseCase;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.runtime.LocalRuntimeException;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldes.EventStream;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.openldes.ldesadmin.model.ldio.StatusChangeSource;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.sandbox.Sandbox;

@ExtendWith(MockitoExtension.class)
class SandboxServiceTest {

  private static final URI EVENT_STREAM_FOR_LDIO =
      URI.create("http://sandbox-ldes-server:8080/sandbox-parcels");

  @Mock private InstanceRepository instanceRepository;
  @Mock private StartLocalInstanceUseCase startLocalInstance;
  @Mock private UnregisterInstanceUseCase unregisterInstance;
  @Mock private EventStreamUseCase eventStreams;
  @Mock private ViewUseCase views;
  @Mock private PipelineUseCase pipelines;

  private final Instance server = TestFixtures.localLdesServer();
  private final Instance ldio = TestFixtures.localLdio();
  private SandboxService service;

  @BeforeEach
  void setUp() {
    service =
        new SandboxService(
            instanceRepository,
            startLocalInstance,
            unregisterInstance,
            eventStreams,
            views,
            pipelines);
  }

  @Test
  void findsTheLocalSandboxInstances() {
    Instance registered =
        Instance.register(
            InstanceType.LDES_SERVER,
            Sandbox.LDES_SERVER_NAME,
            URI.create("http://ldes.example.org"),
            true,
            TestFixtures.NOW);
    when(instanceRepository.findByType(InstanceType.LDES_SERVER))
        .thenReturn(List.of(registered, server));
    when(instanceRepository.findByType(InstanceType.LDIO)).thenReturn(List.of());

    Sandbox sandbox = service.get();

    assertThat(sandbox.ldesServer()).isEqualTo(server);
    assertThat(sandbox.state()).isEqualTo(Sandbox.State.PARTIAL);
  }

  @Test
  void startsBothInstancesAndPreparesThem() {
    givenNothingRuns();
    when(startLocalInstance.start(
            new StartLocalInstanceCommand(
                InstanceType.LDES_SERVER, Sandbox.LDES_SERVER_NAME, true)))
        .thenReturn(server);
    when(startLocalInstance.start(
            new StartLocalInstanceCommand(InstanceType.LDIO, Sandbox.LDIO_NAME, true)))
        .thenReturn(ldio);
    when(eventStreams.list(server.id())).thenReturn(List.of());
    when(pipelines.list(ldio.id())).thenReturn(List.of());

    Sandbox sandbox = service.start();

    assertThat(sandbox.state()).isEqualTo(Sandbox.State.RUNNING);
    verify(eventStreams)
        .create(
            eq(server.id()),
            argThat(
                definition ->
                    definition
                            .content()
                            .contains("@prefix server:  <http://sandbox-ldes-server:8080/>")
                        && definition
                            .content()
                            .contains("server:sandbox-parcels a ldes:EventStream")));
    verify(views)
        .create(
            eq(server.id()),
            eq(Sandbox.EVENT_STREAM),
            argThat(definition -> definition.content().contains("server:by-page")));
    verify(views)
        .create(
            eq(server.id()),
            eq(Sandbox.EVENT_STREAM),
            argThat(
                definition ->
                    definition.content().contains("server:by-time")
                        && definition
                            .content()
                            .contains("tree:HierarchicalTimeBasedFragmentation")));
    verify(pipelines).create(ldio.id(), SandboxDefinitions.ingestPipeline(EVENT_STREAM_FOR_LDIO));
  }

  @Test
  void leavesWhatIsAlreadyPreparedAlone() {
    givenRunning(server, ldio);
    when(eventStreams.list(server.id()))
        .thenReturn(
            List.of(
                new EventStream(
                    Sandbox.EVENT_STREAM, null, null, false, List.of(), RdfDocument.turtle(""))));
    when(pipelines.list(ldio.id()))
        .thenReturn(
            List.of(
                new DeployedPipeline(
                    SandboxDefinitions.ingestPipeline(EVENT_STREAM_FOR_LDIO),
                    PipelineStatus.RUNNING,
                    StatusChangeSource.AUTO)));

    service.start();

    verify(startLocalInstance, never()).start(any());
    verify(eventStreams, never()).create(any(), any());
    verify(views, never()).create(any(), any(), any());
    verify(pipelines, never()).create(any(), any());
    verify(pipelines, never()).update(any(), any());
  }

  @Test
  void redeploysAnIngestPipelineThatFeedsAnotherServer() {
    givenRunning(server, ldio);
    when(eventStreams.list(server.id()))
        .thenReturn(
            List.of(
                new EventStream(
                    Sandbox.EVENT_STREAM, null, null, false, List.of(), RdfDocument.turtle(""))));
    when(pipelines.list(ldio.id()))
        .thenReturn(
            List.of(
                new DeployedPipeline(
                    SandboxDefinitions.ingestPipeline(
                        URI.create("http://sandbox-ldes-server-2:8080/x")),
                    PipelineStatus.RUNNING,
                    StatusChangeSource.AUTO)));

    service.start();

    verify(pipelines).update(ldio.id(), SandboxDefinitions.ingestPipeline(EVENT_STREAM_FOR_LDIO));
  }

  @Test
  void reportsAnInstanceThatDoesNotStart() {
    givenRunning(server, null);
    when(startLocalInstance.start(any())).thenThrow(new LocalRuntimeException("No Docker", null));

    assertThatThrownBy(() -> service.start()).isInstanceOf(LocalRuntimeException.class);
    verify(eventStreams, never()).create(any(), any());
  }

  @Test
  void stopsBothInstances() {
    givenRunning(server, ldio);

    service.stop();

    verify(unregisterInstance).unregister(InstanceType.LDES_SERVER, server.id());
    verify(unregisterInstance).unregister(InstanceType.LDIO, ldio.id());
  }

  private void givenNothingRuns() {
    givenRunning(null, null);
  }

  private void givenRunning(Instance ldesServer, Instance ldioInstance) {
    when(instanceRepository.findByType(InstanceType.LDES_SERVER))
        .thenReturn(ldesServer == null ? List.of() : List.of(ldesServer));
    when(instanceRepository.findByType(InstanceType.LDIO))
        .thenReturn(ldioInstance == null ? List.of() : List.of(ldioInstance));
  }
}
