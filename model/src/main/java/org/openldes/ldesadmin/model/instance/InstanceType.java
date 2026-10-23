package org.openldes.ldesadmin.model.instance;

/**
 * The kinds of OpenLDES applications LDES-admin can manage.
 */
public enum InstanceType {
  /** An OpenLDES server, managed through its admin API. */
  LDES_SERVER,
  /** A Linked Data Interactions Orchestrator, managed through its pipeline API. */
  LDIO
}
