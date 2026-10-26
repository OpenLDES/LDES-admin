package org.openldes.ldesadmin.application.service.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldesadmin.application.port.out.persistence.InstanceRepository;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentRuntime;
import org.openldes.ldesadmin.application.service.TestFixtures;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.rdf.RdfDocument;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldesadmin.model.sandbox.PipelineRun;
import org.openldes.ldesadmin.model.sandbox.RunInput;
import org.openldes.ldesadmin.model.sandbox.RunOptions;
import org.openldes.ldesadmin.model.sandbox.StepResult;
import org.openldes.ldesadmin.model.sandbox.StepResult.Format;
import org.openldes.ldesadmin.model.sandbox.StepResult.Kind;
import org.openldes.ldesadmin.model.sandbox.StepResult.Status;

@ExtendWith(MockitoExtension.class)
class RunPipelineServiceTest {

  private static final RunInput INPUT = RunInput.of("[{\"id\": 1}, {\"id\": 2}]",
      "application/json");
  private static final RunOptions OPTIONS = new RunOptions("session-1", false);
  private static final ComponentDefinition ADAPTER = ComponentDefinition.of("Ldio:JsonToLdAdapter");
  private static final ComponentDefinition FILTER =
      ComponentDefinition.of("Ldio:ChangeDetectionFilter");
  private static final ComponentDefinition SPARQL =
      ComponentDefinition.of("Ldio:SparqlConstructTransformer");
  private static final ComponentDefinition CONSOLE = ComponentDefinition.of("Ldio:ConsoleOut");
  private static final ComponentDefinition HTTP_OUT = new ComponentDefinition("Ldio:HttpOut",
      Map.of("endpoint", "http://sandbox-ldes-server:8080/sandbox-parcels"));

  @Mock
  private InstanceRepository instanceRepository;
  @Mock
  private ComponentRuntime runtime;

  private RunPipelineService service;

  @BeforeEach
  void setUp() {
    service = new RunPipelineService(instanceRepository, runtime);
  }

  @Test
  void runsEveryModelThroughTheTransformersAndWritesWhatReachesTheOutputs() {
    when(runtime.adapt("session-1", ADAPTER, INPUT)).thenReturn(List.of(turtle("a"), turtle("b")));
    when(runtime.transform("session-1", 0, FILTER, turtle("a"))).thenReturn(List.of(turtle("a")));
    when(runtime.transform("session-1", 0, FILTER, turtle("b"))).thenReturn(List.of(turtle("b")));
    when(runtime.transform("session-1", 1, SPARQL, turtle("a")))
        .thenReturn(List.of(turtle("a1"), turtle("a2")));
    when(runtime.transform("session-1", 1, SPARQL, turtle("b"))).thenReturn(List.of(turtle("b1")));
    when(runtime.write(eq("session-1"), eq(0), eq(CONSOLE), any(), eq(false)))
        .thenAnswer(invocation -> new OutputResult(
            invocation.<RdfDocument>getArgument(3).content(), "text/turtle", "Printed"));

    PipelineRun run = service.run(pipeline(List.of(FILTER, SPARQL), List.of(CONSOLE)), INPUT,
        OPTIONS);

    assertThat(run.steps()).extracting(StepResult::kind, StepResult::component,
            StepResult::status, StepResult::summary)
        .containsExactly(
            tuple(Kind.INPUT, "Ldio:HttpIn", Status.OK,
                "Received POST /parcels (application/json)"),
            tuple(Kind.ADAPTER, "Ldio:JsonToLdAdapter", Status.OK,
                "Made 2 models"),
            tuple(Kind.TRANSFORMER, "Ldio:ChangeDetectionFilter",
                Status.OK, "2 models in, 2 models out"),
            tuple(Kind.TRANSFORMER,
                "Ldio:SparqlConstructTransformer", Status.OK, "2 models in, 3 models out"),
            tuple(Kind.OUTPUT, "Ldio:ConsoleOut", Status.OK,
                "Printed (3 models)"));
    assertThat(run.steps().getFirst().outputs()).containsExactly(INPUT.content());
    assertThat(run.steps().getFirst().format()).isEqualTo(Format.JSON);
    assertThat(run.steps().getLast().outputs()).containsExactly("a1", "a2", "b1");
    assertThat(run.delivered()).isTrue();
    assertThat(run.members()).containsExactly("a1", "a2", "b1");
  }

  @Test
  void stopsWhenATransformerFiltersEverythingOut() {
    when(runtime.adapt("session-1", ADAPTER, INPUT)).thenReturn(List.of(turtle("a")));
    when(runtime.transform("session-1", 0, FILTER, turtle("a"))).thenReturn(List.of());

    PipelineRun run = service.run(pipeline(List.of(FILTER, SPARQL), List.of(CONSOLE)), INPUT,
        OPTIONS);

    assertThat(run.steps()).hasSize(3);
    assertThat(run.steps().getLast()).extracting(StepResult::status, StepResult::summary)
        .containsExactly(Status.FILTERED, "Filtered out");
    assertThat(run.delivered()).isFalse();
    assertThat(run.members()).isEmpty();
  }

  @Test
  void stopsAtAComponentThatFails() {
    when(runtime.adapt("session-1", ADAPTER, INPUT))
        .thenThrow(new ComponentException("Not valid JSON: unexpected end of input"));

    PipelineRun run = service.run(pipeline(List.of(FILTER), List.of(CONSOLE)), INPUT, OPTIONS);

    assertThat(run.steps()).extracting(StepResult::kind, StepResult::status)
        .containsExactly(tuple(Kind.INPUT, Status.OK),
            tuple(Kind.ADAPTER, Status.ERROR));
    assertThat(run.steps().getLast().summary()).isEqualTo("Not valid JSON: unexpected end of input");
  }

  @Test
  void anOutputThatFailsDoesNotStopTheOthers() {
    when(runtime.adapt("session-1", ADAPTER, INPUT)).thenReturn(List.of(turtle("a")));
    when(runtime.write(anyString(), eq(0), any(), any(), anyBoolean()))
        .thenThrow(new ComponentException("POST answered 503"));
    // doReturn: calling write to stub it would run into the stub above
    doReturn(new OutputResult("a", "text/turtle", "Printed"))
        .when(runtime).write(anyString(), eq(1), any(), any(), anyBoolean());

    PipelineRun run = service.run(pipeline(List.of(), List.of(HTTP_OUT, CONSOLE)), INPUT, OPTIONS);

    assertThat(run.steps()).extracting(StepResult::status)
        .containsExactly(Status.OK, Status.OK, Status.ERROR, Status.OK);
    assertThat(run.delivered()).isTrue();
  }

  @Test
  void readsWhatAnInputWithoutAdapterReceivesAsLinkedData() {
    RunInput member = RunInput.of("<a> <b> <c> .", "text/turtle");
    when(runtime.read(member)).thenReturn(List.of(turtle("<a> <b> <c> .")));
    Pipeline pipeline = new Pipeline("replicate", null,
        new InputDefinition("Ldio:LdesClient", Map.of("urls", List.of("http://ldes/by-page")), null),
        List.of(), List.of(CONSOLE));
    when(runtime.write(anyString(), anyInt(), any(), any(), anyBoolean()))
        .thenReturn(new OutputResult("", "text/turtle", "Printed"));

    PipelineRun run = service.run(pipeline, member, OPTIONS);

    assertThat(run.steps().getFirst().summary())
        .isEqualTo("Received a member of http://ldes/by-page: 1 model");
    assertThat(run.steps().getFirst().outputs()).containsExactly("<a> <b> <c> .");
  }

  @Test
  void reachesLocalInstancesOnTheUrlOfLdesAdminAndExecutesOutputsWhenAsked() {
    when(instanceRepository.findByType(InstanceType.LDES_SERVER))
        .thenReturn(List.of(TestFixtures.localLdesServer()));
    when(runtime.adapt("session-1", ADAPTER, INPUT)).thenReturn(List.of(turtle("a")));
    ComponentDefinition reachable = new ComponentDefinition("Ldio:HttpOut",
        Map.of("endpoint", "http://localhost:32769/sandbox-parcels"));
    when(runtime.write("session-1", 0, reachable, turtle("a"), true))
        .thenReturn(new OutputResult("a", "text/turtle",
            "POST http://localhost:32769/sandbox-parcels: 200"));

    PipelineRun run = service.run(pipeline(List.of(), List.of(HTTP_OUT)), INPUT,
        new RunOptions("session-1", true));

    verify(runtime).write("session-1", 0, reachable, turtle("a"), true);
    assertThat(run.steps().getLast().summary())
        .isEqualTo("POST http://localhost:32769/sandbox-parcels: 200");
  }

  private static Pipeline pipeline(List<ComponentDefinition> transformers,
      List<ComponentDefinition> outputs) {
    return new Pipeline("parcels", null, new InputDefinition("Ldio:HttpIn", Map.of(), ADAPTER),
        transformers, outputs);
  }

  private static RdfDocument turtle(String content) {
    return RdfDocument.turtle(content);
  }
}
