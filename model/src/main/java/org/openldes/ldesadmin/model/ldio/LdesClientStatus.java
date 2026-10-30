package org.openldes.ldesadmin.model.ldio;

/** Progress of a pipeline whose input is an LDES client. */
public enum LdesClientStatus {
  /** Reading the members that were already in the LDES */
  REPLICATING,
  /** Caught up; following new members */
  SYNCHRONISING,
  /** The LDES is closed and fully read */
  COMPLETED,
  ERROR
}
