package org.openldes.ldesadmin.application.service.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.in.sandbox.RunPipelineUseCase;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentRuntime;
import org.openldes.ldesadmin.application.port.out.sandbox.LiveSourceRuntime;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.sandbox.LivePipelineRun;
import org.openldes.ldesadmin.model.sandbox.PipelineRun;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldesadmin.model.sandbox.RunOptions;

@ExtendWith(MockitoExtension.class)
class LivePipelineServiceTest {
  private static final Pipeline PIPELINE =
      new Pipeline(
          "live",
          "",
          new InputDefinition("Ldio:HttpIn", Map.of(), ComponentDefinition.of("Ldio:RdfAdapter")),
          List.of(),
          List.of(ComponentDefinition.of("Ldio:NoopOut")));
  private static final RunInput INPUT = RunInput.of("<a> <b> <c> .", "text/turtle");
  @Mock private LiveSourceRuntime runtime;
  @Mock private LiveSourceRuntime.Source source;
  @Mock private RunPipelineUseCase runner;
  @Mock private ComponentRuntime components;
  @Mock private InstanceRepository instances;
  private LivePipelineService service;
  private Consumer<RunInput> receiver;
  private Consumer<String> failure;

  @BeforeEach
  void setUp() {
    service = new LivePipelineService(runtime, runner, components, instances);
  }

  private void source() {
    when(runtime.start(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              receiver = invocation.getArgument(1);
              failure = invocation.getArgument(2);
              return source;
            });
    service.start(PIPELINE, new RunOptions("browser", false));
  }

  @Test
  void runsReceivedMembersOnAnIsolatedSessionAndRetainsOnlyTheLatestResult() {
    source();
    PipelineRun result = new PipelineRun(List.of(), true, List.of("member"));
    when(runner.run(any(), any(), any())).thenReturn(result);

    receiver.accept(INPUT);
    receiver.accept(INPUT);

    assertThat(service.get("browser")).isEqualTo(new LivePipelineRun(true, 2, result, null));
    ArgumentCaptor<RunOptions> options = ArgumentCaptor.forClass(RunOptions.class);
    verify(runner, times(2)).run(any(), any(), options.capture());
    assertThat(options.getAllValues())
        .allSatisfy(
            option -> {
              assertThat(option.session()).isNotEqualTo("browser");
              assertThat(option.executeOutputs()).isFalse();
            });
    service.send("browser", INPUT);
    verify(source).send(INPUT);
  }

  @Test
  void stopsTheSourceAndReleasesItsComponents() {
    source();
    service.stop("browser");

    verify(source).close();
    verify(components).release(any());
    assertThat(service.get("browser").active()).isFalse();
    assertThatThrownBy(() -> service.send("browser", INPUT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stopped");
  }

  @Test
  void reportsSourceFailuresAndIgnoresLateResults() {
    source();

    failure.accept("Broker unavailable");
    receiver.accept(INPUT);

    assertThat(service.get("browser"))
        .isEqualTo(new LivePipelineRun(false, 0, null, "Broker unavailable"));
    verify(components).release(any());
  }

  @Test
  void limitsConcurrentSourcesAndAllowsReplacingTheSameSession() {
    when(runtime.start(any(), any(), any())).thenReturn(source);
    for (int i = 0; i < 8; i++) {
      service.start(PIPELINE, new RunOptions("session-" + i, false));
    }

    assertThatThrownBy(() -> service.start(PIPELINE, new RunOptions("overflow", false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At most 8");
    service.start(PIPELINE, new RunOptions("session-0", false));
    verify(source).close();
  }
}
