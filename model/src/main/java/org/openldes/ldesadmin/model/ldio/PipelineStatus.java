package org.openldes.ldesadmin.model.ldio;

/**
 * Status of a pipeline running on an LDIO.
 *
 * <pre>
 * INIT ──▶ RUNNING ⇄ HALTED
 *   └─────────┴────────┴──▶ STOPPED
 * </pre>
 *
 * <p>{@code STARTING}, {@code RESUMING} and {@code STOPPING} are transitional.
 */
public enum PipelineStatus {
  INIT,
  STARTING,
  RESUMING,
  RUNNING,
  HALTED,
  STOPPING,
  STOPPED;

  /**
   * Only a running pipeline can be paused.
   */
  public boolean canBeHalted() {
    return this == RUNNING;
  }

  public boolean canBeResumed() {
    return this == HALTED;
  }
}
