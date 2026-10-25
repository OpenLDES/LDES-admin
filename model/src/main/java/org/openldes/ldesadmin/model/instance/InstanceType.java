package org.openldes.ldesadmin.model.instance;

/**
 * The kinds of OpenLDES applications LDES-admin can manage.
 */
public enum InstanceType {
  /** An OpenLDES server, managed through its admin API. */
  LDES_SERVER("LDES server"),
  /** A Linked Data Interactions Orchestrator, managed through its pipeline API. */
  LDIO("LDIO");

  private final String label;

  InstanceType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
