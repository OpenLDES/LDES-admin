package org.openldes.ldesadmin.adapter.out.ldio;

import java.util.List;
import java.util.Locale;
import org.openldes.ldesadmin.adapter.out.ldio.LdioApi.ComponentTO;
import org.openldes.ldesadmin.adapter.out.ldio.LdioApi.InputComponentTO;
import org.openldes.ldesadmin.adapter.out.ldio.LdioApi.PipelineConfigTO;
import org.openldes.ldesadmin.adapter.out.ldio.LdioApi.PipelineTO;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.DeployedPipeline;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.openldes.ldesadmin.model.ldio.StatusChangeSource;

final class LdioPipelineMapper {

  private LdioPipelineMapper() {
  }

  static DeployedPipeline toModel(PipelineTO pipeline) {
    return new DeployedPipeline(
        new Pipeline(pipeline.name(), pipeline.description(), toModel(pipeline.input()),
            toModel(pipeline.transformers()), toModel(pipeline.outputs())),
        parseStatus(pipeline.status()),
        pipeline.updateSource() == null ? null
            : StatusChangeSource.valueOf(pipeline.updateSource().toUpperCase(Locale.ROOT)));
  }

  static PipelineConfigTO toConfig(Pipeline pipeline) {
    InputDefinition input = pipeline.input();
    return new PipelineConfigTO(pipeline.name(), pipeline.description(),
        new InputComponentTO(input.name(), input.config(),
            input.optionalAdapter().map(LdioPipelineMapper::toTransferObject).orElse(null)),
        pipeline.transformers().stream().map(LdioPipelineMapper::toTransferObject).toList(),
        pipeline.outputs().stream().map(LdioPipelineMapper::toTransferObject).toList());
  }

  /**
   * Status endpoints answer with a bare enum value, sometimes quoted.
   */
  static PipelineStatus parseStatus(String status) {
    if (status == null) {
      return PipelineStatus.INIT;
    }
    return PipelineStatus.valueOf(status.replace("\"", "").strip().toUpperCase(Locale.ROOT));
  }

  private static InputDefinition toModel(InputComponentTO input) {
    return new InputDefinition(input.name(), input.config(),
        input.adapter() == null ? null : toModel(input.adapter()));
  }

  private static List<ComponentDefinition> toModel(List<ComponentTO> components) {
    return components == null ? List.of()
        : components.stream().map(LdioPipelineMapper::toModel).toList();
  }

  private static ComponentDefinition toModel(ComponentTO component) {
    return new ComponentDefinition(component.name(), component.config());
  }

  private static ComponentTO toTransferObject(ComponentDefinition component) {
    return new ComponentTO(component.name(), component.config());
  }
}
