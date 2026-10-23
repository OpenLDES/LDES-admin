package org.openldes.ldesadmin.adapter.in.rest.instance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

/**
 * Adds an existing LDES server or LDIO by its URL.
 *
 * @param sandbox optional, defaults to {@code false}
 */
public record RegisterInstanceRequest(@NotBlank String name, @NotNull URI url, Boolean sandbox) {

  boolean isSandbox() {
    return Boolean.TRUE.equals(sandbox);
  }
}
