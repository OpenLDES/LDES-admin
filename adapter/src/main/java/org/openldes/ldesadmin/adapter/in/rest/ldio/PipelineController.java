package org.openldes.ldesadmin.adapter.in.rest.ldio;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineLifecycleUseCase;
import org.openldes.ldesadmin.application.port.in.ldio.PipelineUseCase;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.ldio.PipelineStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ldios/{ldioId}/pipelines")
class PipelineController {

  private final PipelineUseCase pipelineUseCase;
  private final PipelineLifecycleUseCase pipelineLifecycleUseCase;

  PipelineController(PipelineUseCase pipelineUseCase,
      PipelineLifecycleUseCase pipelineLifecycleUseCase) {
    this.pipelineUseCase = pipelineUseCase;
    this.pipelineLifecycleUseCase = pipelineLifecycleUseCase;
  }

  @GetMapping
  List<DeployedPipelineWebModel> list(@PathVariable UUID ldioId) {
    return pipelineUseCase.list(new InstanceId(ldioId)).stream()
        .map(DeployedPipelineWebModel::fromDomain)
        .toList();
  }

  @GetMapping("/{pipeline}")
  DeployedPipelineWebModel get(@PathVariable UUID ldioId, @PathVariable String pipeline) {
    return DeployedPipelineWebModel.fromDomain(pipelineUseCase.get(new InstanceId(ldioId),
        pipeline));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  DeployedPipelineWebModel create(@PathVariable UUID ldioId,
      @Valid @RequestBody PipelineWebModel pipeline) {
    return DeployedPipelineWebModel.fromDomain(pipelineUseCase.create(new InstanceId(ldioId),
        pipeline.toDomain()));
  }

  @PutMapping("/{pipeline}")
  DeployedPipelineWebModel update(@PathVariable UUID ldioId, @PathVariable String pipeline,
      @Valid @RequestBody PipelineWebModel definition) {
    if (!pipeline.equals(definition.name())) {
      throw new IllegalArgumentException("A pipeline cannot be renamed, expected name "
          + pipeline + " but got " + definition.name());
    }
    return DeployedPipelineWebModel.fromDomain(pipelineUseCase.update(new InstanceId(ldioId),
        definition.toDomain()));
  }

  @DeleteMapping("/{pipeline}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID ldioId, @PathVariable String pipeline) {
    pipelineUseCase.delete(new InstanceId(ldioId), pipeline);
  }

  @GetMapping("/{pipeline}/status")
  PipelineStatusWebModel status(@PathVariable UUID ldioId, @PathVariable String pipeline) {
    return new PipelineStatusWebModel(pipeline,
        pipelineLifecycleUseCase.status(new InstanceId(ldioId), pipeline));
  }

  @PostMapping("/{pipeline}/halt")
  PipelineStatusWebModel halt(@PathVariable UUID ldioId, @PathVariable String pipeline) {
    return new PipelineStatusWebModel(pipeline,
        pipelineLifecycleUseCase.halt(new InstanceId(ldioId), pipeline));
  }

  @PostMapping("/{pipeline}/resume")
  PipelineStatusWebModel resume(@PathVariable UUID ldioId, @PathVariable String pipeline) {
    return new PipelineStatusWebModel(pipeline,
        pipelineLifecycleUseCase.resume(new InstanceId(ldioId), pipeline));
  }

  record PipelineStatusWebModel(String pipeline, PipelineStatus status) {

  }
}
