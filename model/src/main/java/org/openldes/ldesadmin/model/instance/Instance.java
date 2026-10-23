package org.openldes.ldesadmin.model.instance;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * An LDES server or LDIO managed by LDES-admin. This is the aggregate root of the instance
 * registry.
 *
 * <p>An instance is either {@link InstanceOrigin#REGISTERED registered} (it already runs somewhere
 * and LDES-admin only knows its URL), or {@link InstanceOrigin#LOCAL local} (LDES-admin started it
 * and can stop it again). Sandbox instances are meant for experimenting: the sandbox ingests
 * members in them and deploys trial pipelines on them.
 *
 * @param url              base URL of the instance, without trailing slash
 * @param runtimeReference reference to the local runtime of a {@code LOCAL} instance, e.g. a
 *                         container id; {@code null} for registered instances
 */
public record Instance(
    InstanceId id,
    InstanceType type,
    String name,
    URI url,
    InstanceOrigin origin,
    boolean sandbox,
    String runtimeReference,
    Instant registeredAt) {

  public Instance {
    Objects.requireNonNull(id, "'id' must not be null");
    Objects.requireNonNull(type, "'type' must not be null");
    Objects.requireNonNull(origin, "'origin' must not be null");
    Objects.requireNonNull(registeredAt, "'registeredAt' must not be null");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("'name' must not be blank");
    }
    if (origin == InstanceOrigin.LOCAL && runtimeReference == null) {
      throw new IllegalArgumentException("A local instance needs a runtime reference");
    }
    if (origin == InstanceOrigin.REGISTERED && runtimeReference != null) {
      throw new IllegalArgumentException("A registered instance has no runtime reference");
    }
    name = name.strip();
    url = normalize(url);
  }

  /**
   * Registers an instance that already runs somewhere.
   */
  public static Instance register(InstanceType type, String name, URI url, boolean sandbox,
      Instant now) {
    return new Instance(InstanceId.randomId(), type, name, url, InstanceOrigin.REGISTERED, sandbox,
        null, now);
  }

  /**
   * Creates an instance for something LDES-admin just started locally.
   */
  public static Instance local(InstanceType type, String name, URI url, String runtimeReference,
      boolean sandbox, Instant now) {
    return new Instance(InstanceId.randomId(), type, name, url, InstanceOrigin.LOCAL, sandbox,
        runtimeReference, now);
  }

  public boolean isLocal() {
    return origin == InstanceOrigin.LOCAL;
  }

  /**
   * Resolves a path relative to the base URL of this instance.
   */
  public URI resolve(String path) {
    return URI.create(url + "/" + stripLeadingSlashes(path));
  }

  public static URI normalize(URI url) {
    Objects.requireNonNull(url, "'url' must not be null");
    String scheme = url.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)
        || url.getHost() == null) {
      throw new IllegalArgumentException("'url' must be an absolute http(s) URL, but was " + url);
    }
    return URI.create(url.toString().replaceAll("/+$", ""));
  }

  private static String stripLeadingSlashes(String path) {
    return path.replaceAll("^/+", "");
  }
}
