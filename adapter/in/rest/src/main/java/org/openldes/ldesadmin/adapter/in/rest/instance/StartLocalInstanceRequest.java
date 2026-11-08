package org.openldes.ldesadmin.adapter.in.rest.instance;

import jakarta.validation.constraints.NotBlank;

/**
 * Starts a new LDES server or LDIO on the machine running LDES-admin.
 *
 * @param sandbox optional, defaults to {@code false}
 */
public record StartLocalInstanceRequest(@NotBlank String name, Boolean sandbox) {

  boolean isSandbox() {
    return Boolean.TRUE.equals(sandbox);
  }
}
