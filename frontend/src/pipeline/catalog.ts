import type { ComponentConfig } from '@/domain/ldio'

/**
 * The LDIO components that can be used in the sandbox, with the metadata the editor needs: what
 * they do, how they are configured and how they are drawn.
 *
 * Source: https://openldes.github.io/Linked-Data-Interactions/3.2.0-SNAPSHOT/ldio/
 */
export type ComponentKind = 'input' | 'adapter' | 'transformer' | 'output'

export interface ConfigProperty {
  /** Dotted keys are nested in YAML, e.g. rdf-writer.content-type */
  key: string
  required?: boolean
  default?: string
  description: string
}

export interface ComponentInfo {
  id: string
  kind: ComponentKind
  label: string
  description: string
  icon: string
  docs: string
  /** Inputs that receive non-linked data need an adapter */
  requiresAdapter?: boolean
  properties: ConfigProperty[]
  /** Config of a freshly dropped component: required properties with example values */
  defaultConfig: ComponentConfig
}

export interface KindInfo {
  label: string
  plural: string
  description: string
  icon: string
  color: string
}

export const kinds: Record<ComponentKind, KindInfo> = {
  input: {
    label: 'Input',
    plural: 'Inputs',
    description: 'Receives or fetches data and starts the pipeline',
    icon: 'pi pi-sign-in',
    color: '#0ea5e9',
  },
  adapter: {
    label: 'Adapter',
    plural: 'Adapters',
    description: 'Turns the raw data of an input into linked data',
    icon: 'pi pi-arrow-right-arrow-left',
    color: '#8b5cf6',
  },
  transformer: {
    label: 'Transformer',
    plural: 'Transformers',
    description: 'Modifies the linked data model',
    icon: 'pi pi-sliders-h',
    color: '#f59e0b',
  },
  output: {
    label: 'Output',
    plural: 'Outputs',
    description: 'Sends the linked data to its destination',
    icon: 'pi pi-sign-out',
    color: '#10b981',
  },
}

export const kindOrder: ComponentKind[] = ['input', 'adapter', 'transformer', 'output']

const DOCS = 'https://openldes.github.io/Linked-Data-Interactions/3.2.0-SNAPSHOT/ldio'

const rdfWriter: ConfigProperty[] = [
  {
    key: 'rdf-writer.content-type',
    default: 'text/turtle',
    description: 'Serialisation of the output, any content type supported by Apache Jena',
  },
  {
    key: 'rdf-writer.frame',
    description: 'JSON-LD frame to shape the output when writing application/ld+json',
  },
]

const auth: ConfigProperty[] = [
  {
    key: 'auth.type',
    default: 'NO_AUTH',
    description: 'NO_AUTH, API_KEY or OAUTH2_CLIENT_CREDENTIALS',
  },
  { key: 'auth.api-key', description: 'The API key, for auth.type API_KEY' },
  { key: 'auth.client-id', description: 'Client id, for OAUTH2_CLIENT_CREDENTIALS' },
  { key: 'auth.client-secret', description: 'Client secret, for OAUTH2_CLIENT_CREDENTIALS' },
  { key: 'auth.token-endpoint', description: 'Token endpoint, for OAUTH2_CLIENT_CREDENTIALS' },
]

const state: ConfigProperty[] = [
  {
    key: 'state',
    default: 'memory',
    description: "Where the state is kept: 'memory', 'sqlite' or 'postgres'",
  },
  {
    key: 'keep-state',
    default: 'false',
    description: 'Keep the state when the pipeline stops (not for memory)',
  },
]

export const components: ComponentInfo[] = [
  // Inputs ------------------------------------------------------------------------------------
  {
    id: 'Ldio:HttpIn',
    kind: 'input',
    label: 'HTTP In',
    description: 'Listens for data posted to /{pipeline name}',
    icon: 'pi pi-globe',
    docs: `${DOCS}/ldio-inputs/ldio-http-in`,
    requiresAdapter: true,
    properties: [],
    defaultConfig: {},
  },
  {
    id: 'Ldio:HttpInPoller',
    kind: 'input',
    label: 'HTTP In Poller',
    description: 'Polls a URL on an interval or cron schedule',
    icon: 'pi pi-refresh',
    docs: `${DOCS}/ldio-inputs/ldio-http-in-poller`,
    requiresAdapter: true,
    properties: [
      { key: 'url', required: true, description: 'URL to poll' },
      { key: 'interval', description: 'Polling interval in ISO 8601, e.g. PT1M (or use cron)' },
      { key: 'cron', description: 'Spring cron expression, e.g. */10 * * * * * (or interval)' },
      {
        key: 'continueOnFail',
        default: 'true',
        description: 'Keep polling when a poll fails',
      },
      ...auth,
    ],
    defaultConfig: { url: 'https://api.example.org/changes', interval: 'PT1M' },
  },
  {
    id: 'Ldio:LdesClient',
    kind: 'input',
    label: 'LDES Client',
    description: 'Replicates and synchronises an LDES',
    icon: 'pi pi-cloud-download',
    docs: `${DOCS}/ldio-inputs/ldio-ldes-client`,
    properties: [
      { key: 'urls', required: true, description: 'URLs of the LDES (views) to follow' },
      {
        key: 'source-format',
        default: 'text/turtle',
        description: 'Content type to request from the server',
      },
      ...state,
      {
        key: 'enable-exactly-once',
        default: 'true',
        description: 'Filter out members that were already received',
      },
      {
        key: 'materialisation.enabled',
        default: 'false',
        description: 'Turn version objects into state objects',
      },
    ],
    defaultConfig: { urls: ['http://localhost:8080/parcels/by-page'] },
  },
  {
    id: 'Ldio:LdesClientConnector',
    kind: 'input',
    label: 'LDES Client Connector',
    description: 'LDES Client behind an Eclipse Dataspace Connector',
    icon: 'pi pi-lock',
    docs: `${DOCS}/ldio-inputs/ldio-ldes-client-connector`,
    properties: [
      {
        key: 'connector-transfer-url',
        required: true,
        description: 'Transfer URL of the EDC connector that starts the transfer',
      },
      { key: 'proxy-url-to-replace', description: 'Part of the LDES URL to proxy' },
      { key: 'proxy-url-replacement', description: 'Replacement for the proxied URL part' },
    ],
    defaultConfig: {
      'connector-transfer-url': 'http://consumer-connector:29193/management/v2/transferprocesses',
    },
  },
  {
    id: 'Ldio:KafkaIn',
    kind: 'input',
    label: 'Kafka In',
    description: 'Consumes messages from Kafka topics',
    icon: 'pi pi-inbox',
    docs: `${DOCS}/ldio-inputs/ldio-kafka-in`,
    requiresAdapter: true,
    properties: [
      { key: 'content-type', required: true, description: 'Content type of the messages' },
      { key: 'bootstrap-servers', required: true, description: 'Comma separated broker URIs' },
      { key: 'topics', required: true, description: 'Comma separated topic names' },
      { key: 'group-id', description: 'Consumer group, generated by default' },
      { key: 'security-protocol', default: 'NO_AUTH', description: 'e.g. SASL_SSL_PLAIN' },
    ],
    defaultConfig: {
      'content-type': 'application/n-quads',
      'bootstrap-servers': 'localhost:9092',
      topics: 'quickstart-events',
    },
  },
  {
    id: 'Ldio:AmqpIn',
    kind: 'input',
    label: 'AMQP In',
    description: 'Consumes messages from an AMQP queue',
    icon: 'pi pi-envelope',
    docs: `${DOCS}/ldio-inputs/ldio-amqp-in`,
    requiresAdapter: true,
    properties: [
      { key: 'remote-url', required: true, description: 'URI of the AMQP broker' },
      { key: 'queue', required: true, description: 'Name of the queue' },
      { key: 'username', required: true, description: 'User name' },
      { key: 'password', required: true, description: 'Password' },
      { key: 'content-type', default: 'application/n-quads', description: 'Message content type' },
    ],
    defaultConfig: {
      'remote-url': 'amqp://localhost:61616',
      queue: 'quickstart-events',
      username: 'client',
      password: 'secret',
    },
  },
  // Adapters ----------------------------------------------------------------------------------
  {
    id: 'Ldio:RdfAdapter',
    kind: 'adapter',
    label: 'RDF Adapter',
    description: 'Parses RDF in any serialisation Apache Jena supports',
    icon: 'pi pi-share-alt',
    docs: `${DOCS}/ldio-adapters/ldio-rdf-adapter`,
    properties: [
      {
        key: 'max-jsonld-cache-capacity',
        default: '100',
        description: 'Number of external JSON-LD contexts to cache',
      },
    ],
    defaultConfig: {},
  },
  {
    id: 'Ldio:JsonToLdAdapter',
    kind: 'adapter',
    label: 'JSON to JSON-LD',
    description: 'Adds a JSON-LD context to plain JSON',
    icon: 'pi pi-code',
    docs: `${DOCS}/ldio-adapters/ldio-json-to-json-ld`,
    properties: [
      { key: 'context', required: true, description: 'URI of a JSON-LD context, or the context' },
      {
        key: 'force-content-type',
        default: 'false',
        description: 'Treat every message as application/json',
      },
    ],
    defaultConfig: { context: 'https://example.org/context.jsonld' },
  },
  {
    id: 'Ldio:RmlAdapter',
    kind: 'adapter',
    label: 'RML Adapter',
    description: 'Maps JSON, CSV or XML to RDF with an RML mapping',
    icon: 'pi pi-table',
    docs: `${DOCS}/ldio-adapters/ldio-rml-adapter`,
    properties: [
      { key: 'mapping', required: true, description: 'Path to, or content of, the RML mapping' },
    ],
    defaultConfig: { mapping: 'mapping.ttl' },
  },
  {
    id: 'Ldio:NgsiV2ToLdAdapter',
    kind: 'adapter',
    label: 'NGSI-v2 to LD',
    description: 'Converts NGSI-v2 entities into NGSI-LD',
    icon: 'pi pi-sitemap',
    docs: `${DOCS}/ldio-adapters/ldio-ngsiv2-to-ld`,
    properties: [
      { key: 'core-context', required: true, description: 'URI of the core JSON-LD context' },
      { key: 'ld-context', description: 'URI of a custom JSON-LD context' },
      { key: 'data-identifier', required: true, description: 'Key that holds the entities' },
    ],
    defaultConfig: {
      'core-context': 'https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld',
      'data-identifier': 'data',
    },
  },
  // Transformers ------------------------------------------------------------------------------
  {
    id: 'Ldio:VersionObjectCreator',
    kind: 'transformer',
    label: 'Version Object Creator',
    description: 'Turns a state object into a version object',
    icon: 'pi pi-history',
    docs: `${DOCS}/ldio-transformers/ldio-version-object-creator`,
    properties: [
      {
        key: 'member-type',
        required: true,
        description: 'RDF type of the object to turn into a version object',
      },
      {
        key: 'date-observed-property',
        description: 'Property path (<iri>) of the timestamp, defaults to now',
      },
      { key: 'delimiter', default: '/', description: 'Between the state id and the timestamp' },
      {
        key: 'generatedAt-property',
        default: 'http://www.w3.org/ns/prov#generatedAtTime',
        description: 'Property that receives the timestamp',
      },
      {
        key: 'versionOf-property',
        default: 'http://purl.org/dc/terms/isVersionOf',
        description: 'Property that links the version to the state object',
      },
    ],
    defaultConfig: { 'member-type': 'https://example.org/Thing' },
  },
  {
    id: 'Ldio:VersionMaterialiser',
    kind: 'transformer',
    label: 'Version Materialiser',
    description: 'Turns a version object back into a state object',
    icon: 'pi pi-box',
    docs: `${DOCS}/ldio-transformers/ldio-version-materializer`,
    properties: [
      {
        key: 'versionOf-property',
        required: true,
        description: 'Property that links the version to the state object',
      },
      {
        key: 'restrict-to-members',
        default: 'false',
        description: 'Only keep the statements of the member itself',
      },
    ],
    defaultConfig: { 'versionOf-property': 'http://purl.org/dc/terms/isVersionOf' },
  },
  {
    id: 'Ldio:SparqlConstructTransformer',
    kind: 'transformer',
    label: 'SPARQL Construct',
    description: 'Reshapes the model with a SPARQL CONSTRUCT query',
    icon: 'pi pi-filter',
    docs: `${DOCS}/ldio-transformers/ldio-sparql-construct`,
    properties: [
      { key: 'query', required: true, description: 'Path to, or content of, the query' },
      { key: 'infer', default: 'false', description: 'Add the result to the original model' },
    ],
    defaultConfig: { query: 'CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }' },
  },
  {
    id: 'Ldio:GeoJsonToWktTransformer',
    kind: 'transformer',
    label: 'GeoJSON to WKT',
    description: 'Replaces GeoJSON geometries with WKT literals',
    icon: 'pi pi-map',
    docs: `${DOCS}/ldio-transformers/ldio-geojson-to-wkt`,
    properties: [
      {
        key: 'transform-to-rdf+wkt-enabled',
        default: 'false',
        description: 'Produce RDF+WKT instead of GeoSPARQL',
      },
    ],
    defaultConfig: {},
  },
  {
    id: 'Ldio:SkolemisationTransformer',
    kind: 'transformer',
    label: 'Skolemisation',
    description: 'Replaces blank nodes with well-known IRIs',
    icon: 'pi pi-link',
    docs: `${DOCS}/ldio-transformers/ldio-skolemisation-transformer`,
    properties: [{ key: 'skolemDomain', required: true, description: 'Domain of the IRIs' }],
    defaultConfig: { skolemDomain: 'https://example.org' },
  },
  {
    id: 'Ldio:ChangeDetectionFilter',
    kind: 'transformer',
    label: 'Change Detection Filter',
    description: 'Drops members that did not change since the last time',
    icon: 'pi pi-eye',
    docs: `${DOCS}/ldio-transformers/ldio-change-detection-filter`,
    properties: [...state],
    defaultConfig: {},
  },
  {
    id: 'Ldio:HttpEnricher',
    kind: 'transformer',
    label: 'HTTP Enricher',
    description: 'Adds the response of an HTTP call to the model',
    icon: 'pi pi-plus-circle',
    docs: `${DOCS}/ldio-transformers/ldio-http-enricher`,
    properties: [
      { key: 'adapter.name', required: true, description: 'Adapter that parses the response' },
      {
        key: 'url-property-path',
        required: true,
        description: 'Property path that selects the URL to call',
      },
      { key: 'header-property-path', description: 'Property path that selects headers' },
      { key: 'body-property-path', description: 'Property path that selects the body' },
      {
        key: 'http-method-property-path',
        default: 'GET',
        description: 'Property path that selects the method',
      },
      ...auth,
    ],
    defaultConfig: {
      adapter: { name: 'Ldio:RdfAdapter' },
      'url-property-path': '<http://example.org/meta>/<http://example.org/url>',
    },
  },
  // Outputs -----------------------------------------------------------------------------------
  {
    id: 'Ldio:ConsoleOut',
    kind: 'output',
    label: 'Console Out',
    description: 'Writes the model to the LDIO log',
    icon: 'pi pi-desktop',
    docs: `${DOCS}/ldio-outputs/ldio-console-out`,
    properties: [...rdfWriter],
    defaultConfig: {},
  },
  {
    id: 'Ldio:HttpOut',
    kind: 'output',
    label: 'HTTP Out',
    description: 'Posts the model to a URL, e.g. an LDES ingest endpoint',
    icon: 'pi pi-send',
    docs: `${DOCS}/ldio-outputs/ldio-http-out`,
    properties: [
      { key: 'endpoint', required: true, description: 'Target URL' },
      {
        key: 'continueOnFail',
        default: 'true',
        description: 'Drop the member instead of failing when the target keeps refusing it',
      },
      ...rdfWriter,
    ],
    defaultConfig: { endpoint: 'http://localhost:8080/my-collection' },
  },
  {
    id: 'Ldio:HttpSparqlOut',
    kind: 'output',
    label: 'HTTP SPARQL Out',
    description: 'Writes the model to a SPARQL endpoint',
    icon: 'pi pi-database',
    docs: `${DOCS}/ldio-outputs/ldio-http-sparql-out`,
    properties: [
      { key: 'endpoint', required: true, description: 'URL of the SPARQL host' },
      { key: 'graph', description: 'Graph to write to' },
      {
        key: 'replacement.enabled',
        default: 'true',
        description: 'Replace the previous version of the resource',
      },
      { key: 'skolemisation.skolemDomain', description: 'Skolemise blank nodes first' },
      ...auth,
    ],
    defaultConfig: { endpoint: 'http://localhost:8890/sparql' },
  },
  {
    id: 'Ldio:RepositorySink',
    kind: 'output',
    label: 'Repository Sink',
    description: 'Commits the model to an RDF4J repository',
    icon: 'pi pi-server',
    docs: `${DOCS}/ldio-outputs/ldio-repository-sink`,
    properties: [
      { key: 'sparql-host', required: true, description: 'URL of the repository server' },
      { key: 'repository-id', required: true, description: 'RDF4J repository id' },
      { key: 'named-graph', description: 'Named graph to write to' },
      { key: 'batch-size', default: '500', description: 'Members committed at once' },
      { key: 'batch-timeout', default: '120000', description: 'Commit after this many ms' },
    ],
    defaultConfig: { 'sparql-host': 'http://localhost:7200', 'repository-id': 'my-repository' },
  },
  {
    id: 'Ldio:KafkaOut',
    kind: 'output',
    label: 'Kafka Out',
    description: 'Produces the model to a Kafka topic',
    icon: 'pi pi-megaphone',
    docs: `${DOCS}/ldio-outputs/ldio-kafka-out`,
    properties: [
      { key: 'bootstrap-servers', required: true, description: 'Comma separated broker URIs' },
      { key: 'topic', required: true, description: 'Name of the topic' },
      { key: 'key-property-path', description: 'Property path that selects the message key' },
      ...rdfWriter,
    ],
    defaultConfig: { 'bootstrap-servers': 'localhost:9092', topic: 'quickstart-events' },
  },
  {
    id: 'Ldio:AmqpOut',
    kind: 'output',
    label: 'AMQP Out',
    description: 'Sends the model to an AMQP queue',
    icon: 'pi pi-envelope',
    docs: `${DOCS}/ldio-outputs/ldio-amqp-out`,
    properties: [
      { key: 'remote-url', required: true, description: 'URI of the AMQP broker' },
      { key: 'queue', required: true, description: 'Name of the queue' },
      { key: 'username', required: true, description: 'User name' },
      { key: 'password', required: true, description: 'Password' },
      ...rdfWriter,
    ],
    defaultConfig: {
      'remote-url': 'amqp://localhost:61616',
      queue: 'quickstart-events',
      username: 'client',
      password: 'secret',
    },
  },
  {
    id: 'Ldio:LdioRdbOut',
    kind: 'output',
    label: 'Relational Database Out',
    description: 'Inserts rows into a database table via a SPARQL SELECT',
    icon: 'pi pi-th-large',
    docs: `${DOCS}/ldio-outputs/ldio-rdb-out`,
    properties: [
      { key: 'table-name', required: true, description: 'Table that receives the rows' },
      {
        key: 'sparql-select-query',
        required: true,
        description: 'Query that turns the model into rows',
      },
    ],
    defaultConfig: {
      'table-name': 'members',
      'sparql-select-query': 'SELECT ?id ?type WHERE { ?id a ?type }',
    },
  },
  {
    id: 'Ldio:NoopOut',
    kind: 'output',
    label: 'No-op Out',
    description: 'Discards the model; handy while testing',
    icon: 'pi pi-ban',
    docs: `${DOCS}/ldio-outputs/index`,
    properties: [],
    defaultConfig: {},
  },
]

const byId = new Map(components.map((component) => [component.id, component]))

export function componentInfo(id: string): ComponentInfo | undefined {
  return byId.get(id)
}

export function componentsOfKind(kind: ComponentKind): ComponentInfo[] {
  return components.filter((component) => component.kind === kind)
}

/**
 * Reads a dotted key from a config that may be flat (LDIO responses) or nested (YAML).
 */
export function configValue(config: ComponentConfig | undefined, key: string): unknown {
  if (!config) return undefined
  if (key in config) return config[key]
  const [head, ...rest] = key.split('.')
  const nested = config[head]
  return rest.length > 0 && nested && typeof nested === 'object'
    ? configValue(nested as ComponentConfig, rest.join('.'))
    : undefined
}

export function missingRequiredProperties(id: string, config: ComponentConfig = {}): string[] {
  return (componentInfo(id)?.properties ?? [])
    .filter((property) => property.required)
    .filter((property) => {
      const value = configValue(config, property.key)
      return value === undefined || value === null || value === ''
    })
    .map((property) => property.key)
}

const summaryKeys = [
  'endpoint',
  'url',
  'urls',
  'topic',
  'topics',
  'queue',
  'member-type',
  'context',
  'mapping',
  'repository-id',
  'skolemDomain',
  'versionOf-property',
  'table-name',
  'rdf-writer.content-type',
]

/**
 * The most telling config value of a component, shown on its node.
 */
export function summarize(config: ComponentConfig | undefined): string | undefined {
  for (const key of summaryKeys) {
    const value = configValue(config, key)
    if (value !== undefined && value !== null && value !== '') {
      return Array.isArray(value) ? value.join(', ') : String(value)
    }
  }
  return undefined
}
