package org.openldes.ldesadmin.adapter.out.sandbox;

import org.apache.jena.sparql.util.Context;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldi.JsonToLdAdapter;
import org.openldes.ldi.NgsiV2ToLdAdapter;
import org.openldes.ldi.RdfAdapter;
import org.openldes.ldi.rdf.parser.JenaContextProvider;
import org.openldes.ldi.types.LdiAdapter;

/** The adapters of LDIO, configured as their LDIO configurators do it. */
final class Adapters {

  private Adapters() {}

  static LdiAdapter create(String name, LdioConfig config) {
    return switch (name) {
      case "Ldio:RdfAdapter" -> new RdfAdapter(jenaContext(config));
      case "Ldio:JsonToLdAdapter" ->
          new JsonToLdAdapter(
              config.property("context"),
              config.optionalBoolean("force-content-type").orElse(false),
              jenaContext(config));
      case "Ldio:RmlAdapter" -> new RmlRecordAdapter(config.property("mapping"));
      case "Ldio:NgsiV2ToLdAdapter" ->
          new NgsiV2ToLdAdapter(
              config.property("data-identifier"),
              config.property("core-context"),
              config.optional("ld-context").orElse(null));
      default -> throw new ComponentException(name + " is not an adapter the sandbox can run");
    };
  }

  private static Context jenaContext(LdioConfig config) {
    return JenaContextProvider.create()
        .withMaxJsonLdCacheCapacity(config.optionalInteger("max-jsonld-cache-capacity").orElse(100))
        .getContext();
  }
}
