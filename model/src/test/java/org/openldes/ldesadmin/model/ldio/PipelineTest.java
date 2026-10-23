package org.openldes.ldesadmin.model.ldio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PipelineTest {

  private static final InputDefinition HTTP_IN =
      new InputDefinition("Ldio:HttpIn", null, ComponentDefinition.of("Ldio:RdfAdapter"));

  @Test
  void defaultsMissingCollectionsToEmptyOnes() {
    Pipeline pipeline = new Pipeline("my-pipeline", null, HTTP_IN, null, null);

    assertThat(pipeline.description()).isEmpty();
    assertThat(pipeline.transformers()).isEmpty();
    assertThat(pipeline.outputs()).isEmpty();
    assertThat(pipeline.input().optionalAdapter()).contains(ComponentDefinition.of(
        "Ldio:RdfAdapter"));
  }

  @Test
  void keepsTheOrderOfComponentConfig() {
    ComponentDefinition output = new ComponentDefinition("Ldio:HttpOut", Map.of(
        "endpoint", "http://localhost:8080/parcels"));
    Pipeline pipeline = new Pipeline("parcels", "Parcels to LDES", HTTP_IN, List.of(),
        List.of(output));

    assertThat(pipeline.outputs()).containsExactly(output);
    assertThatThrownBy(() -> pipeline.outputs().getFirst().config().put("foo", "bar"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "with space", "slash/name", "-leading-dash"})
  void rejectsNamesThatCannotBeUsedInUrls(String name) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Pipeline(name, null, HTTP_IN, null, null));
  }

  @Test
  void onlyARunningPipelineCanBeHalted() {
    assertThat(PipelineStatus.RUNNING.canBeHalted()).isTrue();
    assertThat(PipelineStatus.HALTED.canBeHalted()).isFalse();
    assertThat(PipelineStatus.INIT.canBeHalted()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = PipelineStatus.class, names = "HALTED", mode = EnumSource.Mode.EXCLUDE)
  void onlyAHaltedPipelineCanBeResumed(PipelineStatus status) {
    assertThat(status.canBeResumed()).isFalse();
    assertThat(PipelineStatus.HALTED.canBeResumed()).isTrue();
  }

  @Test
  void statisticsReportMembersInFlight() {
    assertThat(new PipelineStatistics("p", 120, 100).inFlight()).isEqualTo(20);
    assertThat(new PipelineStatistics("p", 100, 120).inFlight()).isZero();
  }
}
