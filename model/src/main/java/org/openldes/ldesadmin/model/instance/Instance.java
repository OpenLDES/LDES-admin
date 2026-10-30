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
 * @param url base URL on which LDES-admin reaches the instance, without trailing slash
 * @param internalUrl base URL on which other local instances reach a {@code LOCAL} instance, e.g.
 *     {@code http://sandbox-ldes-server:8080}; {@code null} for registered instances
 * @param runtimeReference reference to the local runtime of a {@code LOCAL} instance, e.g. a
 *     container id; {@code null} for registered instances
 */
public record Instance(
    InstanceId id,
    InstanceType type,
    String name,
    URI url,
    URI internalUrl,
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
    if (origin == InstanceOrigin.REGISTERED && (runtimeReference != null || internalUrl != null)) {
      throw new IllegalArgumentException(
          "A registered instance has no runtime reference or internal URL");
    }
    name = name.strip();
    url = normalize(url);
    internalUrl = internalUrl == null ? null : normalize(internalUrl);
  }

  /** Registers an instance that already runs somewhere. */
  public static Instance register(
      InstanceType type, String name, URI url, boolean sandbox, Instant now) {
    return new Instance(
        InstanceId.randomId(),
        type,
        name,
        url,
        null,
        InstanceOrigin.REGISTERED,
        sandbox,
        null,
        now);
  }

  /** Creates an instance for something LDES-admin just started locally. */
  public static Instance local(
      InstanceType type,
      String name,
      URI url,
      URI internalUrl,
      String runtimeReference,
      boolean sandbox,
      Instant now) {
    return new Instance(
        InstanceId.randomId(),
        type,
        name,
        url,
        internalUrl,
        InstanceOrigin.LOCAL,
        sandbox,
        runtimeReference,
        now);
  }

  public boolean isLocal() {
    return origin == InstanceOrigin.LOCAL;
  }

  /** Resolves a path relative to the base URL of this instance. */
  public URI resolve(String path) {
    return URI.create(url + "/" + stripLeadingSlashes(path));
  }

  /**
   * The URL on which LDES-admin can fetch an IRI this instance published. A local instance
   * publishes IRIs on its internal URL, which only other local instances can resolve.
   */
  public URI reachable(String iri) {
    if (internalUrl != null && iri.startsWith(internalUrl.toString())) {
      return URI.create(url + iri.substring(internalUrl.toString().length()));
    }
    return URI.create(iri);
  }

  /** Whether a URL points to this instance, so LDES-admin may fetch it on behalf of a user. */
  public boolean serves(URI candidate) {
    String target = candidate.toString();
    return target.equals(url.toString())
        || target.startsWith(url + "/")
        || target.startsWith(url + "?");
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
