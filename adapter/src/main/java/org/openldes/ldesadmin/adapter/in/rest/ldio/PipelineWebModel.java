package org.openldes.ldesadmin.adapter.in.rest.ldio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.openldes.ldesadmin.model.ldio.ComponentDefinition;
import org.openldes.ldesadmin.model.ldio.InputDefinition;
import org.openldes.ldesadmin.model.ldio.Pipeline;

/**
 * A pipeline definition, in the same JSON structure as the LDIO pipeline API.
 */
public record PipelineWebModel(
    @NotBlank String name,
    String description,
    @NotNull @Valid InputWebModel input,
    List<@Valid ComponentWebModel> transformers,
    List<@Valid ComponentWebModel> outputs) {

  public static PipelineWebModel fromDomain(Pipeline pipeline) {
    return new PipelineWebModel(pipeline.name(), pipeline.description(),
        InputWebModel.fromDomain(pipeline.input()),
        ComponentWebModel.fromDomain(pipeline.transformers()),
        ComponentWebModel.fromDomain(pipeline.outputs()));
  }

  public Pipeline toDomain() {
    return new Pipeline(name, description, input.toDomain(),
        transformers == null ? null : transformers.stream().map(ComponentWebModel::toDomain)
            .toList(),
        outputs == null ? null : outputs.stream().map(ComponentWebModel::toDomain).toList());
  }

  public record InputWebModel(@NotBlank String name, Map<String, Object> config,
                              @Valid ComponentWebModel adapter) {

    static InputWebModel fromDomain(InputDefinition input) {
      return new InputWebModel(input.name(), input.config(),
          input.optionalAdapter().map(ComponentWebModel::fromDomain).orElse(null));
    }

    InputDefinition toDomain() {
      return new InputDefinition(name, config, adapter == null ? null : adapter.toDomain());
    }
  }

  public record ComponentWebModel(@NotBlank String name, Map<String, Object> config) {

    static ComponentWebModel fromDomain(ComponentDefinition component) {
      return new ComponentWebModel(component.name(), component.config());
    }

    static List<ComponentWebModel> fromDomain(List<ComponentDefinition> components) {
      return components.stream().map(ComponentWebModel::fromDomain).toList();
    }

    ComponentDefinition toDomain() {
      return new ComponentDefinition(name, config);
    }
  }
}
