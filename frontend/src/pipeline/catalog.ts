import type { ComponentConfig } from '@/domain/ldio'
import { SANDBOX_EVENT_STREAM_URL } from '@/domain/sandbox'

/**
 * The LDIO components that can be used in the sandbox, with the metadata the editor needs: what
 * they do, how they are configured and how they are drawn.
 *
 * Source: https://openldes.github.io/Linked-Data-Interactions/3.2.0-SNAPSHOT/ldio/
 *
 * Defaults that point to an LDES point to the event stream of the sandbox, so a pipeline deployed
 * on the sandbox LDIO works out of the box.
 */
export type ComponentKind = 'input' | 'adapter' | 'transformer' | 'output'

/**
 * What a property holds, which decides how it is edited and validated:
 *
 * * `string`, `secret`, `text` – free text: one line, hidden, or several lines (queries, mappings)
 * * `url`, `urls` – an http(s) URL, or a list of them
 * * `iri`, `propertyPath` – an absolute IRI, or a property path of IRIs in angle brackets
 * * `integer`, `boolean`, `duration` (ISO 8601), `cron` (Spring, 6 fields)
 * * `select` – one of a list of options
 * * `context` – a JSON-LD context: its URL, or the context document itself
 * * `json` – a JSON document, as a string
 * * `headers` – a list of HTTP headers, each a key and a value
 * * `map` – a nested configuration, only edited in YAML
 */
export type PropertyType =
  | 'string'
  | 'secret'
  | 'text'
  | 'url'
  | 'urls'
  | 'iri'
  | 'propertyPath'
  | 'integer'
  | 'boolean'
  | 'duration'
  | 'cron'
  | 'select'
  | 'context'
  | 'json'
  | 'headers'
  | 'map'

export interface SelectOption {
  value: string
  label?: string
}

export interface ConfigProperty {
  /** Dotted keys are nested in YAML, e.g. rdf-writer.content-type */
  key: string
  type: PropertyType
  required?: boolean
  default?: string
  description: string
  /** Shown as placeholder when there is no default */
  example?: string
  /** For `select`: the values to choose from */
  options?: SelectOption[]
  /** For `select`: the options are suggestions; other values are allowed too */
  custom?: boolean
  /** For `string`: the format the value has to follow */
  pattern?: { regex: RegExp; message: string }
  /** For `integer`: the lowest value */
  min?: number
  /** For `urls`: a single URL is written as a value rather than a list */
  single?: boolean
  /** Groups related properties in the editor, e.g. Authentication; ungrouped ones come first */
  group?: string
  /** Only applies when another property has one of these values (or its default is one) */
  when?: { key: string; values: string[] }
}

export interface ConfigProblem {
  /** The property it is about; none for problems of the configuration as a whole */
  key?: string
  /** For list properties: the item it is about */
  index?: number
  severity: 'error' | 'warning'
  message: string
}

/** Reads the configuration of a component in the checks of the component */
export interface ConfigReader {
  /** The value as configured, undefined when it is not */
  value(key: string): unknown
  /** The value as configured, or else the default, as text */
  effective(key: string): string | undefined
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
  /** Rules that involve several properties */
  check?: (config: ConfigReader) => ConfigProblem[]
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

/** The RDF serialisations Apache Jena reads and writes */
export const RDF_CONTENT_TYPES: SelectOption[] = [
  { value: 'text/turtle', label: 'Turtle' },
  { value: 'application/n-quads', label: 'N-Quads' },
  { value: 'application/n-triples', label: 'N-Triples' },
  { value: 'application/trig', label: 'TriG' },
  { value: 'application/ld+json', label: 'JSON-LD' },
  { value: 'application/rdf+xml', label: 'RDF/XML' },
  { value: 'application/rdf+json', label: 'RDF/JSON' },
  { value: 'application/trix+xml', label: 'TriX' },
  { value: 'text/n3', label: 'N3' },
  { value: 'application/rdf+protobuf', label: 'RDF Protobuf' },
  { value: 'application/rdf+thrift', label: 'RDF Thrift' },
]

/** What messages of an input with an adapter can hold: RDF, or raw data for the adapter */
const MESSAGE_CONTENT_TYPES: SelectOption[] = [
  ...RDF_CONTENT_TYPES,
  { value: 'application/json', label: 'JSON' },
  { value: 'text/csv', label: 'CSV' },
  { value: 'application/xml', label: 'XML' },
]

const BROKERS = {
  regex: /^\s*[^\s,:]+:\d+(\s*,\s*[^\s,:]+:\d+)*\s*$/,
  message: 'Enter host:port pairs separated by commas, e.g. localhost:9092',
}

const AMQP_URL = {
  regex: /^amqp(s|ws|wss)?:\/\/[^\s/:]+:\d+/,
  message: 'Enter an AMQP URI, e.g. amqp://localhost:61616',
}

const rdfWriter: ConfigProperty[] = [
  {
    key: 'rdf-writer.content-type',
    type: 'select',
    options: RDF_CONTENT_TYPES,
    default: 'text/turtle',
    description: 'Serialisation of the output',
    group: 'Output format',
  },
  {
    key: 'rdf-writer.frame',
    type: 'json',
    example: '{"@context": {...}, "@type": "..."}',
    description: 'JSON-LD frame to shape the JSON-LD output',
    group: 'Output format',
    when: { key: 'rdf-writer.content-type', values: ['application/ld+json'] },
  },
]

const API_KEY = 'API_KEY'
const OAUTH2 = 'OAUTH2_CLIENT_CREDENTIALS'

const auth: ConfigProperty[] = [
  {
    key: 'auth.type',
    type: 'select',
    options: [
      { value: 'NO_AUTH', label: 'No authentication' },
      { value: API_KEY, label: 'API key' },
      { value: OAUTH2, label: 'OAuth2 client credentials' },
    ],
    default: 'NO_AUTH',
    description: 'How to authenticate with the server',
    group: 'Authentication',
  },
  {
    key: 'auth.api-key',
    type: 'secret',
    description: 'The API key',
    group: 'Authentication',
    when: { key: 'auth.type', values: [API_KEY] },
  },
  {
    key: 'auth.api-key-header',
    type: 'string',
    default: 'X-API-KEY',
    description: 'The header that carries the API key',
    group: 'Authentication',
    when: { key: 'auth.type', values: [API_KEY] },
  },
  {
    key: 'auth.client-id',
    type: 'string',
    description: 'The client identifier',
    group: 'Authentication',
    when: { key: 'auth.type', values: [OAUTH2] },
  },
  {
    key: 'auth.client-secret',
    type: 'secret',
    description: 'The client secret',
    group: 'Authentication',
    when: { key: 'auth.type', values: [OAUTH2] },
  },
  {
    key: 'auth.token-endpoint',
    type: 'url',
    example: 'https://login.example.org/token',
    description: 'Where to request a token',
    group: 'Authentication',
    when: { key: 'auth.type', values: [OAUTH2] },
  },
  {
    key: 'auth.scope',
    type: 'string',
    description: 'The OAuth2 scope to request',
    group: 'Authentication',
    when: { key: 'auth.type', values: [OAUTH2] },
  },
]

const httpRequester: ConfigProperty[] = [
  ...auth,
  {
    key: 'retries.enabled',
    type: 'boolean',
    default: 'true',
    description: 'Retry requests when the server cannot be reached',
    group: 'Retries',
  },
  {
    key: 'retries.max',
    type: 'integer',
    min: 0,
    default: '5',
    description: 'How many times to retry',
    group: 'Retries',
    when: { key: 'retries.enabled', values: ['true'] },
  },
  {
    key: 'retries.statuses-to-retry',
    type: 'string',
    example: '410,451',
    pattern: {
      regex: /^\s*\d{3}(\s*,\s*\d{3})*\s*$/,
      message: 'Enter HTTP status codes separated by commas, e.g. 410,451',
    },
    description: 'Other HTTP status codes that trigger a retry',
    group: 'Retries',
    when: { key: 'retries.enabled', values: ['true'] },
  },
  {
    key: 'rate-limit.enabled',
    type: 'boolean',
    default: 'false',
    description: 'Limit the number of requests to the server',
    group: 'Rate limit',
  },
  {
    key: 'rate-limit.limit',
    type: 'integer',
    min: 1,
    default: '500',
    description: 'Requests allowed per period',
    group: 'Rate limit',
    when: { key: 'rate-limit.enabled', values: ['true'] },
  },
  {
    key: 'rate-limit.period',
    type: 'duration',
    default: 'PT1M',
    description: 'The period of the limit',
    group: 'Rate limit',
    when: { key: 'rate-limit.enabled', values: ['true'] },
  },
  {
    key: 'http.headers',
    type: 'headers',
    description: 'Headers to add to every request',
    group: 'HTTP headers',
  },
]

const state: ConfigProperty[] = [
  {
    key: 'state',
    type: 'select',
    options: [
      { value: 'memory', label: 'Memory' },
      { value: 'sqlite', label: 'SQLite' },
      { value: 'postgres', label: 'PostgreSQL' },
    ],
    default: 'memory',
    description: 'Where to keep the state; it survives restarts in SQLite and PostgreSQL',
    group: 'State',
  },
  {
    key: 'keep-state',
    type: 'boolean',
    default: 'false',
    description: 'Keep the state when the pipeline stops',
    group: 'State',
    when: { key: 'state', values: ['sqlite', 'postgres'] },
  },
  {
    key: 'sqlite.directory',
    type: 'string',
    example: '/ldio/sqlite',
    description: 'Directory for the .db file',
    group: 'State',
    when: { key: 'state', values: ['sqlite'] },
  },
  {
    key: 'postgres.url',
    type: 'string',
    example: 'jdbc:postgresql://localhost:5432/ldio',
    pattern: {
      regex: /^jdbc:postgresql:\/\/\S+$/,
      message: 'Enter a JDBC URL, e.g. jdbc:postgresql://localhost:5432/ldio',
    },
    description: 'JDBC URL of the database',
    group: 'State',
    when: { key: 'state', values: ['postgres'] },
  },
  {
    key: 'postgres.username',
    type: 'string',
    description: 'User name for the database',
    group: 'State',
    when: { key: 'state', values: ['postgres'] },
  },
  {
    key: 'postgres.password',
    type: 'secret',
    description: 'Password for the database',
    group: 'State',
    when: { key: 'state', values: ['postgres'] },
  },
]

const kafkaSecurity: ConfigProperty[] = [
  {
    key: 'security-protocol',
    type: 'select',
    options: [
      { value: 'NO_AUTH', label: 'No authentication' },
      { value: 'SASL_SSL_PLAIN', label: 'SASL over SSL, plain' },
    ],
    default: 'NO_AUTH',
    description: 'How to connect to the brokers',
    group: 'Security',
  },
  {
    key: 'sasl-jaas-user',
    type: 'string',
    description: 'User name',
    group: 'Security',
    when: { key: 'security-protocol', values: ['SASL_SSL_PLAIN'] },
  },
  {
    key: 'sasl-jaas-password',
    type: 'secret',
    description: 'Password',
    group: 'Security',
    when: { key: 'security-protocol', values: ['SASL_SSL_PLAIN'] },
  },
]

function isBlank(value: unknown): boolean {
  return value === undefined || value === null || (typeof value === 'string' && value.trim() === '')
}

/** Credentials that the chosen authentication or state needs */
function checkCredentials(config: ConfigReader): ConfigProblem[] {
  const problems: ConfigProblem[] = []
  const need = (key: string, because: string, severity: ConfigProblem['severity'] = 'error') => {
    if (isBlank(config.value(key))) {
      problems.push({ key, severity, message: `Needed for ${because}` })
    }
  }
  if (config.effective('auth.type') === API_KEY) need('auth.api-key', 'API key authentication')
  if (config.effective('auth.type') === OAUTH2) {
    ['auth.client-id', 'auth.client-secret', 'auth.token-endpoint']
      .forEach((key) => need(key, 'OAuth2 client credentials'))
  }
  if (config.effective('state') === 'postgres') {
    ['postgres.url', 'postgres.username', 'postgres.password']
      .forEach((key) => need(key, 'state in PostgreSQL', 'warning'))
  }
  if (config.effective('security-protocol') === 'SASL_SSL_PLAIN') {
    ['sasl-jaas-user', 'sasl-jaas-password'].forEach((key) => need(key, 'SASL'))
  }
  return problems
}

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
      { key: 'url', type: 'urls', single: true, required: true, description: 'URLs to poll' },
      {
        key: 'interval',
        type: 'duration',
        example: 'PT1M',
        description: 'Time between polls, in ISO 8601; or use a cron expression',
      },
      {
        key: 'cron',
        type: 'cron',
        example: '*/10 * * * * *',
        description: 'When to poll, as a Spring cron expression; or use an interval',
      },
      {
        key: 'continueOnFail',
        type: 'boolean',
        default: 'true',
        description: 'Keep polling when a poll fails',
      },
      ...httpRequester,
    ],
    defaultConfig: { url: 'https://api.example.org/changes', interval: 'PT1M' },
    check: (config) => {
      const set = (key: string) => !isBlank(config.value(key))
      const problems = checkCredentials(config)
      if (!set('interval') && !set('cron')) {
        problems.push({ key: 'interval', severity: 'error', message: "Set 'interval' or 'cron'" })
      } else if (set('interval') && set('cron')) {
        problems.push({ key: 'cron', severity: 'warning', message: "Set 'interval' or 'cron', not both" })
      }
      return problems
    },
  },
  {
    id: 'Ldio:LdesClient',
    kind: 'input',
    label: 'LDES Client',
    description: 'Replicates and synchronises an LDES',
    icon: 'pi pi-cloud-download',
    docs: `${DOCS}/ldio-inputs/ldio-ldes-client`,
    properties: [
      {
        key: 'urls',
        type: 'urls',
        required: true,
        description: 'URLs of the LDES to follow; several URLs must be views of the same LDES',
      },
      {
        key: 'source-format',
        type: 'select',
        options: RDF_CONTENT_TYPES,
        default: 'text/turtle',
        description: 'Content type to request from the server; N-Quads or RDF Protobuf parse faster when the server uses no relative IRIs',
      },
      {
        key: 'enable-exactly-once',
        type: 'boolean',
        default: 'true',
        description: 'Filter out members that were already received from another page',
      },
      {
        key: 'materialisation.enabled',
        type: 'boolean',
        default: 'false',
        description: 'Turn version objects into state objects',
        group: 'Version materialisation',
      },
      {
        key: 'materialisation.enable-latest-state',
        type: 'boolean',
        default: 'true',
        description: 'Only pass on the latest state of each object',
        group: 'Version materialisation',
        when: { key: 'materialisation.enabled', values: ['true'] },
      },
      ...state,
      ...httpRequester,
    ],
    defaultConfig: { urls: [`${SANDBOX_EVENT_STREAM_URL}/by-page`] },
    check: (config) => {
      const problems = checkCredentials(config)
      if (config.effective('materialisation.enabled') === 'true'
        && config.effective('enable-exactly-once') === 'true') {
        problems.push({
          key: 'enable-exactly-once',
          severity: 'warning',
          message: 'The exactly-once filter does not work with version materialisation: turn it off',
        })
      }
      return problems
    },
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
        type: 'url',
        required: true,
        description: 'Transfer URL of the EDC connector that starts the transfer',
      },
      {
        key: 'proxy-url-to-replace',
        type: 'string',
        example: 'http://ldes-behind-connectors.dev',
        description: 'Part of the LDES URLs to replace',
      },
      {
        key: 'proxy-url-replacement',
        type: 'string',
        example: 'http://consumer-connector:29193',
        description: 'What replaces that part',
      },
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
      {
        key: 'content-type',
        type: 'select',
        options: MESSAGE_CONTENT_TYPES,
        custom: true,
        required: true,
        description: 'Content type of the messages, as the adapter reads them',
      },
      {
        key: 'bootstrap-servers',
        type: 'string',
        required: true,
        pattern: BROKERS,
        description: 'Brokers to connect to',
      },
      { key: 'topics', type: 'string', required: true, description: 'Topics, separated by commas' },
      { key: 'group-id', type: 'string', description: 'Consumer group; generated when not set' },
      ...kafkaSecurity,
    ],
    defaultConfig: {
      'content-type': 'application/n-quads',
      'bootstrap-servers': 'localhost:9092',
      topics: 'quickstart-events',
    },
    check: checkCredentials,
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
      { key: 'remote-url', type: 'string', required: true, pattern: AMQP_URL, description: 'URI of the AMQP broker' },
      { key: 'queue', type: 'string', required: true, description: 'Name of the queue' },
      { key: 'username', type: 'string', required: true, description: 'User name' },
      { key: 'password', type: 'secret', required: true, description: 'Password' },
      {
        key: 'content-type',
        type: 'select',
        options: MESSAGE_CONTENT_TYPES,
        custom: true,
        default: 'application/n-quads',
        description: 'Content type of the messages, unless they tell otherwise',
      },
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
        type: 'integer',
        min: 1,
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
      {
        key: 'context',
        type: 'context',
        required: true,
        description: 'URL of a JSON-LD context, or the context document: {"@context": {...}}',
      },
      {
        key: 'force-content-type',
        type: 'boolean',
        default: 'false',
        description: 'Read every message as application/json, whatever its content type',
      },
      {
        key: 'max-jsonld-cache-capacity',
        type: 'integer',
        min: 1,
        default: '100',
        description: 'Number of external JSON-LD contexts to cache',
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
      {
        key: 'mapping',
        type: 'text',
        required: true,
        description: 'Path to the RML mapping, or the mapping itself',
      },
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
      { key: 'core-context', type: 'url', required: true, description: 'URL of the core JSON-LD context' },
      { key: 'ld-context', type: 'url', description: 'URL of a custom JSON-LD context' },
      {
        key: 'data-identifier',
        type: 'string',
        required: true,
        description: 'Key of the JSON that holds the entities',
      },
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
        type: 'iri',
        required: true,
        description: 'RDF type of the objects to turn into version objects',
      },
      {
        key: 'date-observed-property',
        type: 'propertyPath',
        example: '<https://example.org/lastModified>',
        description: 'Property path to an xsd:dateTime to use as timestamp; the current time when not set',
      },
      {
        key: 'delimiter',
        type: 'string',
        default: '/',
        description: 'Between the id of the state object and the timestamp, in the version id',
      },
      {
        key: 'generatedAt-property',
        type: 'iri',
        default: 'http://www.w3.org/ns/prov#generatedAtTime',
        description: 'Property that receives the timestamp',
      },
      {
        key: 'versionOf-property',
        type: 'iri',
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
        type: 'iri',
        required: true,
        description: 'Property that links the version to the state object',
      },
      {
        key: 'restrict-to-members',
        type: 'boolean',
        default: 'false',
        description: 'Only keep the statements of the member itself, with its nested blank nodes',
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
      {
        key: 'query',
        type: 'text',
        required: true,
        description: 'Path to the query, or the query itself',
      },
      {
        key: 'infer',
        type: 'boolean',
        default: 'false',
        description: 'Add the result to the original model instead of replacing it',
      },
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
        type: 'boolean',
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
    properties: [
      { key: 'skolemDomain', type: 'iri', required: true, description: 'Domain of the generated IRIs' },
    ],
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
    check: checkCredentials,
  },
  {
    id: 'Ldio:HttpEnricher',
    kind: 'transformer',
    label: 'HTTP Enricher',
    description: 'Adds the response of an HTTP call to the model',
    icon: 'pi pi-plus-circle',
    docs: `${DOCS}/ldio-transformers/ldio-http-enricher`,
    properties: [
      {
        key: 'adapter.name',
        type: 'select',
        options: [
          { value: 'Ldio:RdfAdapter', label: 'RDF Adapter' },
          { value: 'Ldio:JsonToLdAdapter', label: 'JSON to JSON-LD' },
          { value: 'Ldio:RmlAdapter', label: 'RML Adapter' },
          { value: 'Ldio:NgsiV2ToLdAdapter', label: 'NGSI-v2 to LD' },
        ],
        required: true,
        description: 'Adapter that turns the responses into linked data',
      },
      {
        key: 'adapter.config',
        type: 'map',
        description: 'Configuration of that adapter',
      },
      {
        key: 'url-property-path',
        type: 'propertyPath',
        required: true,
        example: '<http://example.org/url>',
        description: 'Property path to the URL to call',
      },
      {
        key: 'header-property-path',
        type: 'propertyPath',
        example: '<http://example.org/header>',
        description: 'Property path to the headers to send',
      },
      {
        key: 'body-property-path',
        type: 'propertyPath',
        example: '<http://example.org/meta>/<http://example.org/body>',
        description: 'Property path to the body to post',
      },
      {
        key: 'http-method-property-path',
        type: 'string',
        default: 'GET',
        description: 'Property path to the HTTP method, GET or POST',
      },
      ...httpRequester,
    ],
    defaultConfig: {
      adapter: { name: 'Ldio:RdfAdapter' },
      'url-property-path': '<http://example.org/meta>/<http://example.org/url>',
    },
    check: checkCredentials,
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
      { key: 'endpoint', type: 'url', required: true, description: 'Where to post the model' },
      ...rdfWriter,
      ...httpRequester,
    ],
    defaultConfig: { endpoint: SANDBOX_EVENT_STREAM_URL },
    check: checkCredentials,
  },
  {
    id: 'Ldio:HttpSparqlOut',
    kind: 'output',
    label: 'HTTP SPARQL Out',
    description: 'Writes the model to a SPARQL endpoint',
    icon: 'pi pi-database',
    docs: `${DOCS}/ldio-outputs/ldio-http-sparql-out`,
    properties: [
      { key: 'endpoint', type: 'url', required: true, description: 'URL of the SPARQL endpoint' },
      { key: 'graph', type: 'iri', description: 'Graph to write to' },
      {
        key: 'skolemisation.skolemDomain',
        type: 'iri',
        description: 'Skolemise blank nodes with this domain before writing',
      },
      {
        key: 'replacement.enabled',
        type: 'boolean',
        default: 'true',
        description: 'Replace the earlier statements about the same subjects',
        group: 'Replacement',
      },
      {
        key: 'replacement.depth',
        type: 'integer',
        min: 1,
        default: '10',
        description: 'How deep nested nodes are deleted',
        group: 'Replacement',
        when: { key: 'replacement.enabled', values: ['true'] },
      },
      {
        key: 'replacement.deleteFunction',
        type: 'text',
        example: 'DELETE { ?s ?p ?o } WHERE { ?s ?p ?o }',
        description: 'A delete query instead of the default one',
        group: 'Replacement',
        when: { key: 'replacement.enabled', values: ['true'] },
      },
      ...httpRequester,
    ],
    defaultConfig: { endpoint: 'http://localhost:8890/sparql' },
    check: checkCredentials,
  },
  {
    id: 'Ldio:RepositorySink',
    kind: 'output',
    label: 'Repository Sink',
    description: 'Commits the model to an RDF4J repository',
    icon: 'pi pi-server',
    docs: `${DOCS}/ldio-outputs/ldio-repository-sink`,
    properties: [
      { key: 'sparql-host', type: 'url', required: true, description: 'URL of the repository server' },
      { key: 'repository-id', type: 'string', required: true, description: 'RDF4J repository id' },
      { key: 'named-graph', type: 'iri', description: 'Named graph to write to' },
      { key: 'batch-size', type: 'integer', min: 1, default: '500', description: 'Members committed at once' },
      {
        key: 'batch-timeout',
        type: 'integer',
        min: 1,
        default: '120000',
        description: 'Milliseconds after which a smaller batch is committed anyway',
      },
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
      {
        key: 'bootstrap-servers',
        type: 'string',
        required: true,
        pattern: BROKERS,
        description: 'Brokers to connect to',
      },
      { key: 'topic', type: 'string', required: true, description: 'Name of the topic' },
      {
        key: 'key-property-path',
        type: 'propertyPath',
        example: '<http://purl.org/dc/terms/title>',
        description: 'Property path to the message key',
      },
      {
        key: 'frame-type',
        type: 'iri',
        description: 'RDF type of the objects to include when framing JSON-LD',
      },
      ...kafkaSecurity,
      ...rdfWriter,
    ],
    defaultConfig: { 'bootstrap-servers': 'localhost:9092', topic: 'quickstart-events' },
    check: checkCredentials,
  },
  {
    id: 'Ldio:AmqpOut',
    kind: 'output',
    label: 'AMQP Out',
    description: 'Sends the model to an AMQP queue',
    icon: 'pi pi-envelope',
    docs: `${DOCS}/ldio-outputs/ldio-amqp-out`,
    properties: [
      { key: 'remote-url', type: 'string', required: true, pattern: AMQP_URL, description: 'URI of the AMQP broker' },
      { key: 'queue', type: 'string', required: true, description: 'Name of the queue' },
      { key: 'username', type: 'string', required: true, description: 'User name' },
      { key: 'password', type: 'secret', required: true, description: 'Password' },
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
      { key: 'table-name', type: 'string', required: true, description: 'Table that receives the rows' },
      {
        key: 'sparql-select-query',
        type: 'text',
        required: true,
        description: 'Query that turns the model into rows',
      },
      {
        key: 'ignore-duplicate-key-exception',
        type: 'boolean',
        default: 'false',
        description: 'Skip rows that violate a unique key instead of failing',
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
 * LDIO matches configuration keys regardless of case, dashes and underscores: source-format,
 * sourceFormat and sourceformat are the same property. Deployed pipelines come back with keys
 * like membertype.
 */
export function normalizeKey(key: string): string {
  return key.toLowerCase().replace(/[-_]/g, '')
}

/**
 * Where a property is in a config that may be nested (YAML), flat (LDIO responses, e.g.
 * rdfwriter.contenttype) or a mix, with keys written in any of the ways LDIO accepts.
 *
 * @returns the keys leading to the value, and the value
 */
export function findConfigEntry(config: ComponentConfig | undefined, key: string):
  { path: string[]; value: unknown } | undefined {
  return config ? find(config, key.split('.').map(normalizeKey)) : undefined
}

function find(config: ComponentConfig, segments: string[]): { path: string[]; value: unknown } | undefined {
  for (const [key, value] of Object.entries(config)) {
    const keySegments = key.split('.').map(normalizeKey)
    if (keySegments.length > segments.length
      || !keySegments.every((segment, i) => segment === segments[i])) {
      continue
    }
    if (keySegments.length === segments.length) return { path: [key], value }
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      const nested = find(value as ComponentConfig, segments.slice(keySegments.length))
      if (nested) return { path: [key, ...nested.path], value: nested.value }
    }
  }
  return undefined
}

/**
 * Reads a dotted key from a config, however it is written (see {@link findConfigEntry}).
 */
export function configValue(config: ComponentConfig | undefined, key: string): unknown {
  return findConfigEntry(config, key)?.value
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
