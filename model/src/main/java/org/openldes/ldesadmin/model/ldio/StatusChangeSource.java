package org.openldes.ldesadmin.model.ldio;

/**
 * Whether the last status change of a pipeline was triggered by the LDIO itself or through the
 * pipeline API.
 */
public enum StatusChangeSource {
  AUTO,
  MANUAL
}
