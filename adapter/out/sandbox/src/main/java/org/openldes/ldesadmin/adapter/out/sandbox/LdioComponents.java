package org.openldes.ldesadmin.adapter.out.sandbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.rdf.model.Model;
import org.openldes.ldesadmin.application.port.out.sandbox.ComponentException;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldi.types.LdiOutput;
import org.openldes.ldio.config.LdioAmqpOutAutoConfig;
import org.openldes.ldio.config.LdioChangeDetectionFilterAutoConfig;
import org.openldes.ldio.config.LdioConsoleOutAutoConfig;
import org.openldes.ldio.config.LdioGeoJsonToWktAutoConfig;
import org.openldes.ldio.config.LdioHttpEnricherAutoConfig;
import org.openldes.ldio.config.LdioHttpOutAutoConfig;
import org.openldes.ldio.config.LdioHttpSparqlOutAutoConfig;
import org.openldes.ldio.config.LdioJsonToLdAdapterAutoConfig;
import org.openldes.ldio.config.LdioKafkaOutAutoConfig;
import org.openldes.ldio.config.LdioNgsiv2ToLdAdapterAutoConfig;
import org.openldes.ldio.config.LdioNoopOutAutoConfig;
import org.openldes.ldio.config.LdioRdbOutAutoConfig;
import org.openldes.ldio.config.LdioRdfAdapterAutoConfig;
import org.openldes.ldio.config.LdioRepositoryPipelineEventsListenerConfig;
import org.openldes.ldio.config.LdioRepositorySinkAutoConfig;
import org.openldes.ldio.config.LdioRmlAdapterAutoConfig;
import org.openldes.ldio.config.LdioSparqlConstructAutoConfig;
import org.openldes.ldio.config.LdioVersionMaterialiserAutoConfig;
import org.openldes.ldio.config.LdioVersionObjectCreatorAutoConfig;
import org.openldes.ldio.config.config.LdioSkolemisationTransformerAutoConfig;
import org.openldes.ldio.pipeline.creation.LdioAdapterConfigurator;
import org.openldes.ldio.pipeline.creation.LdioTransformer;
import org.openldes.ldio.pipeline.creation.LdioTransformerConfigurator;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit LDIO configuration, isolated from the application's bean scan and host files. */
final class LdioComponents implements AutoCloseable {

  private final GenericApplicationContext context = new GenericApplicationContext();
  private final Map<LdiOutput, SingleConnectionDataSource> databases = new ConcurrentHashMap<>();

  LdioComponents() {
    Map<String, LdioAdapterConfigurator> adapters =
        Map.of(
            "Ldio:RdfAdapter",
            new LdioRdfAdapterAutoConfig().ldioAdapterConfigurator(),
            "Ldio:JsonToLdAdapter",
            new LdioJsonToLdAdapterAutoConfig().ldioJsonToLdAdapterConfigurator(),
            "Ldio:RmlAdapter",
            new LdioRmlAdapterAutoConfig().ldioAdapterConfigurator(),
            "Ldio:NgsiV2ToLdAdapter",
            new LdioNgsiv2ToLdAdapterAutoConfig().ldioAdapterConfigurator());
    adapters.forEach(
        (name, configurator) -> context.getBeanFactory().registerSingleton(name, configurator));
    context.refresh();
  }

  static ComponentProperties properties(LdioConfig config) {
    return properties(config, "sandbox");
  }

  static ComponentProperties properties(LdioConfig config, String pipeline) {
    Map<String, String> values = config.values();
    Duration maximum = Duration.ofSeconds(30);
    for (String key :
        List.of("timeouts.connect", "timeouts.connectionrequest", "timeouts.socket")) {
      Duration configured = Duration.parse(values.getOrDefault(key, maximum.toString()));
      if (configured.isNegative() || configured.isZero()) {
        throw new ComponentException(config.component() + " needs a positive duration for " + key);
      }
      values.put(
          key, configured.compareTo(maximum) > 0 ? maximum.toString() : configured.toString());
    }
    return new InlineProperties(pipeline, config.component(), values);
  }

  LdiAdapter adapter(String name, LdioConfig config) {
    if (!context.containsBean(name)) {
      throw new ComponentException(name + " is not an adapter the sandbox can run");
    }
    return (LdiAdapter)
        context.getBean(name, LdioAdapterConfigurator.class).configure(properties(config));
  }

  Transformer transformer(String name, LdioConfig config) {
    LdioTransformerConfigurator configurator =
        switch (name) {
          case "Ldio:VersionObjectCreator" ->
              new LdioVersionObjectCreatorAutoConfig().ldioConfigurator();
          case "Ldio:VersionMaterialiser" ->
              new LdioVersionMaterialiserAutoConfig().ldioConfigurator();
          case "Ldio:SparqlConstructTransformer" ->
              new LdioSparqlConstructAutoConfig().ldioConfigurator();
          case "Ldio:GeoJsonToWktTransformer" ->
              new LdioGeoJsonToWktAutoConfig().geoJsonToWktConfigurator();
          case "Ldio:SkolemisationTransformer" ->
              new LdioSkolemisationTransformerAutoConfig().ldioConfigurator();
          case "Ldio:ChangeDetectionFilter" ->
              new LdioChangeDetectionFilterAutoConfig().ldioChangeDetectionFilterConfigurator();
          case "Ldio:HttpEnricher" -> new LdioHttpEnricherAutoConfig().ldioConfigurator(context);
          default ->
              throw new ComponentException(name + " is not a transformer the sandbox can run");
        };
    ComponentProperties properties = properties(config);
    // A sandbox session never opens a persistent state store on the admin host.
    if (name.equals("Ldio:ChangeDetectionFilter")) {
      properties.getConfig().put("state", "MEMORY");
    }
    LdioTransformer component;
    try {
      component = configurator.configure(properties);
    } catch (QueryParseException e) {
      throw new ComponentException("The query is not valid SPARQL: " + e.getMessage(), e);
    }
    return new CapturedTransformer(component);
  }

  LdiOutput output(String name, LdioConfig config) {
    ComponentProperties properties = properties(config);
    LdiComponent component =
        switch (name) {
          case "Ldio:ConsoleOut" ->
              new LdioConsoleOutAutoConfig().ldioConfigurator().configure(properties);
          case "Ldio:NoopOut" ->
              new LdioNoopOutAutoConfig().ldiHttpOutConfigurator().configure(properties);
          case "Ldio:HttpOut" -> {
            properties.getConfig().put("continueonfail", "false");
            yield new LdioHttpOutAutoConfig().ldiHttpOutConfigurator().configure(properties);
          }
          case "Ldio:HttpSparqlOut" ->
              new LdioHttpSparqlOutAutoConfig()
                  .ldiHttpSparqlOutConfigurator()
                  .configure(properties);
          case "Ldio:KafkaOut" ->
              new LdioKafkaOutAutoConfig().ldiKafkaOutConfigurator().configure(properties);
          case "Ldio:AmqpOut" ->
              new LdioAmqpOutAutoConfig().ldioConfigurator().configure(properties);
          case "Ldio:LdioRdbOut" -> database(properties);
          case "Ldio:RepositorySink" -> {
            properties.getConfig().put("batchsize", "1");
            yield new LdioRepositorySinkAutoConfig()
                .ldiRepoMaterialiserConfigurator(
                    new LdioRepositoryPipelineEventsListenerConfig()
                        .ldioRepositoryMaterialiserLdioPipelineEventsListener())
                .configure(properties);
          }
          default -> throw new ComponentException(name + " is not an output the sandbox can run");
        };
    return (LdiOutput) component;
  }

  private LdiOutput database(ComponentProperties properties) {
    // The upstream configurator queries JDBC metadata even when no member is written.
    SingleConnectionDataSource database =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:sandbox-" + UUID.randomUUID() + ";DB_CLOSE_ON_EXIT=FALSE", true);
    try {
      LdiOutput component =
          (LdiOutput)
              new LdioRdbOutAutoConfig(
                      new JdbcTemplate(database),
                      new TransactionTemplate(new DataSourceTransactionManager(database)))
                  .ldioConfigurator()
                  .configure(properties);
      databases.put(component, database);
      return component;
    } catch (RuntimeException e) {
      database.destroy();
      throw e;
    }
  }

  void close(LdiOutput component) {
    SingleConnectionDataSource database = databases.remove(component);
    if (database != null) {
      database.destroy();
    }
  }

  @Override
  public void close() {
    databases.values().forEach(SingleConnectionDataSource::destroy);
    databases.clear();
    context.close();
  }

  private static final class CapturedTransformer implements Transformer {

    private final LdioTransformer component;
    private final List<Model> results = new ArrayList<>();

    CapturedTransformer(LdioTransformer component) {
      this.component = component;
      LdioTransformer.link(
          component,
          List.of(
              new LdioTransformer() {
                @Override
                public void apply(Model model) {
                  results.add(model);
                }
              }));
    }

    @Override
    public synchronized List<Model> transform(Model model) {
      results.clear();
      try {
        component.apply(model);
        return List.copyOf(results);
      } finally {
        results.clear();
      }
    }
  }

  private static final class InlineProperties extends ComponentProperties {

    InlineProperties(String pipeline, String component, Map<String, String> values) {
      super(pipeline, component, values);
    }

    @Override
    public String getProperty(String key) {
      return getOptionalProperty(key)
          .orElseThrow(
              () -> new ComponentException("%s needs '%s'".formatted(getComponentName(), key)));
    }

    @Override
    public Optional<String> getOptionalPropertyFromFile(String key) {
      return Optional.empty();
    }

    @Override
    public ComponentProperties extractNestedProperties(String key) {
      ComponentProperties nested = super.extractNestedProperties(key);
      return new InlineProperties(getPipelineName(), nested.getComponentName(), nested.getConfig());
    }
  }
}
