package org.openldes.ldesadmin.adapter.in.rest.instance;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.openldes.ldesadmin.application.port.in.instance.RegisterInstanceUseCase.RegisterInstanceCommand;
import org.openldes.ldesadmin.application.port.in.instance.StartLocalInstanceUseCase.StartLocalInstanceCommand;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Registry endpoints shared by LDES servers and LDIOs; subclasses bind them to a path and type.
 */
abstract class InstanceController {

  private final InstanceType type;
  private final String basePath;
  private final InstanceUseCases useCases;

  InstanceController(InstanceType type, String basePath, InstanceUseCases useCases) {
    this.type = type;
    this.basePath = basePath;
    this.useCases = useCases;
  }

  @GetMapping
  List<InstanceWebModel> list() {
    return useCases.find().findAll(type).stream().map(InstanceWebModel::fromDomain).toList();
  }

  @GetMapping("/{id}")
  InstanceWebModel get(@PathVariable UUID id) {
    return InstanceWebModel.fromDomain(useCases.find().get(type, new InstanceId(id)));
  }

  /**
   * Adds an instance that already runs somewhere.
   */
  @PostMapping
  ResponseEntity<InstanceWebModel> register(@Valid @RequestBody RegisterInstanceRequest request) {
    Instance instance = useCases.register().register(new RegisterInstanceCommand(type,
        request.name(), request.url(), request.isSandbox()));
    return created(instance);
  }

  /**
   * Starts a new instance locally. Returns once it accepts requests, which takes a while.
   */
  @PostMapping("/local")
  ResponseEntity<InstanceWebModel> startLocal(
      @Valid @RequestBody StartLocalInstanceRequest request) {
    Instance instance = useCases.startLocal().start(new StartLocalInstanceCommand(type,
        request.name(), request.isSandbox()));
    return created(instance);
  }

  /**
   * Removes an instance; a local instance is stopped as well.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void unregister(@PathVariable UUID id) {
    useCases.unregister().unregister(type, new InstanceId(id));
  }

  @GetMapping("/{id}/health")
  InstanceHealthWebModel health(@PathVariable UUID id) {
    return InstanceHealthWebModel.fromDomain(useCases.checkHealth().check(type,
        new InstanceId(id)));
  }

  private ResponseEntity<InstanceWebModel> created(Instance instance) {
    URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
        .path(basePath)
        .pathSegment(instance.id().toString())
        .build()
        .toUri();
    return ResponseEntity.created(location).body(InstanceWebModel.fromDomain(instance));
  }
}
