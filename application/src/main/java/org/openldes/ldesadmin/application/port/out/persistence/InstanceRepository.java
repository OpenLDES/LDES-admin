package org.openldes.ldesadmin.application.port.out.persistence;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.openldes.ldesadmin.model.instance.Instance;
import org.openldes.ldesadmin.model.instance.InstanceId;
import org.openldes.ldesadmin.model.instance.InstanceType;

/** Stores the LDES servers and LDIOs managed by LDES-admin. */
public interface InstanceRepository {

  void save(Instance instance);

  Optional<Instance> findById(InstanceId id);

  /** All instances of the given type, ordered by name. */
  List<Instance> findByType(InstanceType type);

  boolean existsByTypeAndUrl(InstanceType type, URI url);

  void deleteById(InstanceId id);
}
