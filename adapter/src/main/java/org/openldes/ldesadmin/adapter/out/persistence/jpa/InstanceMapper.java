package org.openldes.ldesadmin.adapter.out.persistence.jpa;

import java.net.URI;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;

final class InstanceMapper {

  private InstanceMapper() {
  }

  static InstanceJpaEntity toJpaEntity(Instance instance) {
    InstanceJpaEntity entity = new InstanceJpaEntity();
    entity.setId(instance.id().value());
    entity.setType(instance.type());
    entity.setName(instance.name());
    entity.setUrl(instance.url().toString());
    entity.setOrigin(instance.origin());
    entity.setSandbox(instance.sandbox());
    entity.setRuntimeReference(instance.runtimeReference());
    entity.setRegisteredAt(instance.registeredAt());
    return entity;
  }

  static Instance toModel(InstanceJpaEntity entity) {
    return new Instance(
        new InstanceId(entity.getId()),
        entity.getType(),
        entity.getName(),
        URI.create(entity.getUrl()),
        entity.getOrigin(),
        entity.isSandbox(),
        entity.getRuntimeReference(),
        entity.getRegisteredAt());
  }
}
