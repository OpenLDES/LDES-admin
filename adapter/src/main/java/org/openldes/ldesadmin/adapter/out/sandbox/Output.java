package org.openldes.ldesadmin.adapter.out.sandbox;

import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldesadmin.model.sandbox.OutputResult;
import org.openldes.ldi.RepositorySink;
import org.openldes.ldi.rdf.formatter.LdiRdfWriter;
import org.openldes.ldi.rdf.formatter.LdiRdfWriterProperties;

/**
 * The outputs of LDIO, configured as their LDIO configurators do it. In a dry run an output shows
 * what it would send; executed, it sends it. Outputs whose client LDES-admin does not have (Kafka,
 * AMQP, a database) always run dry.
 */
@FunctionalInterface
interface Output {

  OutputResult write(Model model, boolean execute);

  static Output create(String name, LdioConfig config) {
    return switch (name) {
      case "Ldio:ConsoleOut" -> {
        // LDIO logs what its writer writes, which applies a JSON-LD frame
        RdfWriter writer = RdfWriter.of(config);
        yield (model, execute) -> new OutputResult(writer.write(model), writer.contentType(),
            "Printed to the console of the LDIO");
      }
      case "Ldio:HttpOut" -> new HttpOut(config);
      case "Ldio:HttpSparqlOut" -> new SparqlOut(config);
      case "Ldio:RepositorySink" -> repositorySink(config);
      case "Ldio:KafkaOut" -> dryRunOnly("Kafka Out",
          "send it to topic " + config.property("topic"), RdfWriter.of(config));
      case "Ldio:AmqpOut" -> dryRunOnly("AMQP Out",
          "send it to queue " + config.property("queue"), RdfWriter.of(config));
      case "Ldio:LdioRdbOut" -> new RdbOut(config);
      case "Ldio:NoopOut" -> (model, execute) -> new OutputResult("", "text/plain", "Did nothing");
      default -> throw new ComponentException(name + " is not an output the sandbox can run");
    };
  }

  /**
   * The repository sink of LDIO: adds every model to a repository of an RDF4J server. LDIO
   * batches the models; the sandbox adds each one at once.
   */
  private static Output repositorySink(LdioConfig config) {
    String host = config.property("sparql-host");
    String repository = config.property("repository-id");
    String namedGraph = config.optional("named-graph").orElse("");
    return (model, execute) -> {
      String turtle = Rdf.toTurtle(model);
      if (!execute) {
        return new OutputResult(turtle, Lang.TURTLE.getHeaderString(),
            "Would add the model to repository %s of %s".formatted(repository, host));
      }
      var sink = new RepositorySink(host, repository, namedGraph);
      try {
        sink.process(List.of(model));
      } finally {
        sink.shutdown();
      }
      return new OutputResult(turtle, Lang.TURTLE.getHeaderString(),
          "Added the model to repository %s of %s".formatted(repository, host));
    };
  }

  private static Output dryRunOnly(String label, String action, RdfWriter writer) {
    return (model, execute) -> new OutputResult(writer.write(model), writer.contentType(),
        execute
            ? "%s only runs dry in the sandbox: it would %s".formatted(label, action)
            : "Would " + action);
  }

  /**
   * The RDF writer of an output, configured with its {@code rdf-writer} properties.
   */
  record RdfWriter(LdiRdfWriter writer, String contentType) {

    static RdfWriter of(LdioConfig config) {
      var properties = new LdiRdfWriterProperties(config.nested("rdf-writer"));
      if (properties.getLang() == null) {
        throw new ComponentException("%s cannot write %s".formatted(config.component(),
            config.nested("rdf-writer").get("contenttype")));
      }
      return new RdfWriter(LdiRdfWriter.getRdfWriter(properties),
          properties.getLang().getHeaderString());
    }

    String write(Model model) {
      return writer.write(model);
    }
  }
}
