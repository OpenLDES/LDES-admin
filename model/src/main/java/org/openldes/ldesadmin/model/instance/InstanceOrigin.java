package org.openldes.ldesadmin.model.instance;

/** Who controls the lifecycle of an instance. */
public enum InstanceOrigin {
  /** An existing instance that was added by its URL. LDES-admin does not start or stop it. */
  REGISTERED,
  /** An instance that LDES-admin started locally (as a Docker container) and can stop again. */
  LOCAL
}
