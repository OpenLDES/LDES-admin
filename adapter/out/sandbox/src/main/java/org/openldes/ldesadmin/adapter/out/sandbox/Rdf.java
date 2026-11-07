package org.openldes.ldesadmin.adapter.out.sandbox;

import java.io.StringWriter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserRegistry;
import org.apache.jena.riot.RiotException;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;

/** Models as the steps of a run pass them on: Turtle, with the prefixes of the model. */
final class Rdf {

  private Rdf() {}

  static Model fromTurtle(String turtle) {
    return parse(turtle, Lang.TURTLE);
  }

  static String toTurtle(Model model) {
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, model, RDFFormat.TURTLE_PRETTY);
    return writer.toString();
  }

  /** Linked data in the RDF format of its content type, e.g. a member an LDES client receives. */
  static Model parse(String content, String contentType) {
    Lang lang = RDFLanguages.contentTypeToLang(contentType.split(";")[0].strip());
    if (lang == null || !RDFParserRegistry.isRegistered(lang)) {
      throw new ComponentException("%s is not an RDF format".formatted(contentType));
    }
    return parse(content, lang);
  }

  private static Model parse(String content, Lang lang) {
    try {
      return RDFParser.fromString(content, lang).toModel();
    } catch (RiotException e) {
      throw new ComponentException(
          "Not valid %s: %s".formatted(lang.getLabel(), e.getMessage()), e);
    }
  }
}
