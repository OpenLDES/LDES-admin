import type { HealthStatus, Instance } from '@/domain/instance'
import type {
  BackgroundProcess,
  EventStream,
  ServerCatalog,
  StreamStatistics,
  TypedConfig,
  View,
} from '@/domain/ldes'
import { backlogOf } from '@/domain/ldes'
import type {
  DeployedPipeline,
  LdesClientStatus,
  PipelineStatistics,
} from '@/domain/ldio'
import type { ActivityEvent, ThroughputSeries } from '@/domain/dashboard'
import { compact, expand, PREFIXES, prefixLines } from '@/domain/rdf'
import { SANDBOX_EVENT_STREAM, SANDBOX_EVENT_STREAM_URL, SANDBOX_INGEST_PIPELINE } from '@/domain/sandbox'

export { sampleMember } from '@/ldes/memberTemplates'

/**
 * Sample data for the mock API: a production, staging, sandbox and unreachable LDES server, and
 * three LDIOs feeding and consuming them. Modelled after Flemish open data (parcels, addresses,
 * mobility hindrances and water quality observations).
 */
export const IDS = {
  production: '3f6d2a8e-1b7c-4e2a-9c5d-0a1b2c3d4e01',
  staging: '3f6d2a8e-1b7c-4e2a-9c5d-0a1b2c3d4e02',
  sandboxServer: '3f6d2a8e-1b7c-4e2a-9c5d-0a1b2c3d4e03',
  legacy: '3f6d2a8e-1b7c-4e2a-9c5d-0a1b2c3d4e04',
  ingestLdio: '7a1e4c9b-2d3f-4b8a-8e6f-1c2d3e4f5a01',
  consumerLdio: '7a1e4c9b-2d3f-4b8a-8e6f-1c2d3e4f5a02',
  sandboxLdio: '7a1e4c9b-2d3f-4b8a-8e6f-1c2d3e4f5a03',
}

export interface SampleState {
  instances: Instance[]
  health: Record<string, HealthStatus>
  problems: Record<string, string[]>
  /** Per server */
  eventStreams: Record<string, EventStream[]>
  /** Per server and collection: `${serverId}/${collection}` */
  views: Record<string, View[]>
  shapes: Record<string, string>
  /** The catalog of the server itself, per server */
  serverCatalogs: Record<string, ServerCatalog>
  /** The event streams with DCAT dataset metadata, per server */
  datasets: Record<string, string[]>
  statistics: Record<string, StreamStatistics[]>
  processes: Record<string, BackgroundProcess[]>
  /** Per LDIO */
  pipelines: Record<string, DeployedPipeline[]>
  pipelineStatistics: Record<string, PipelineStatistics[]>
  clientStatuses: Record<string, Record<string, LdesClientStatus>>
  activity: ActivityEvent[]
  throughput: ThroughputSeries[]
}

// Event streams ------------------------------------------------------------------------------

interface ShapeProperty {
  path: string
  datatype?: string
  minCount?: number
}

interface ViewSpec {
  name: string
  pageSize: number
  fragmentations?: TypedConfig[]
  retentionPolicies?: TypedConfig[]
  /** Share of the ingested members that are bucketised and paginated in this view */
  bucketised: number
  paginated: number
}

export interface StreamSpec {
  collection: string
  title: string
  description: string
  targetClass: string
  timestampPath: string
  createVersions?: boolean
  properties: ShapeProperty[]
  ingested: number
  views: ViewSpec[]
}

const timeBased = (path: string): TypedConfig => ({
  type: 'HierarchicalTimeBasedFragmentation',
  properties: { maxGranularity: 'day', fragmentationPath: expand(path) },
})
const geospatial: TypedConfig = {
  type: 'GeospatialFragmentation',
  properties: { maxZoom: '15', fragmentationPath: `${PREFIXES.geo}asWKT` },
}

export const streamSpecs: Record<string, StreamSpec[]> = {
  [IDS.production]: [
    {
      collection: 'parcels',
      title: 'Cadastral parcels',
      description: 'Every change to a cadastral parcel in Flanders, as a version object',
      targetClass: 'perceel:Perceel',
      timestampPath: 'prov:generatedAtTime',
      properties: [
        { path: 'perceel:capakey', datatype: 'xsd:string', minCount: 1 },
        { path: 'perceel:municipality', datatype: 'xsd:string' },
        { path: 'geo:asWKT', datatype: 'geo:wktLiteral' },
      ],
      ingested: 1_284_512,
      views: [
        { name: 'by-page', pageSize: 250, bucketised: 1, paginated: 1 },
        {
          name: 'by-time',
          pageSize: 250,
          fragmentations: [timeBased('prov:generatedAtTime')],
          bucketised: 1,
          paginated: 0.9994,
        },
        {
          name: 'by-location',
          pageSize: 100,
          fragmentations: [geospatial],
          bucketised: 0.9981,
          paginated: 0.9939,
        },
      ],
    },
    {
      collection: 'addresses',
      title: 'Addresses',
      description: 'The central address register of Flanders',
      targetClass: 'adres:Adres',
      timestampPath: 'prov:generatedAtTime',
      properties: [
        { path: 'adres:huisnummer', datatype: 'xsd:string', minCount: 1 },
        { path: 'adres:gemeentenaam', datatype: 'xsd:string', minCount: 1 },
        { path: 'adres:postcode', datatype: 'xsd:string' },
      ],
      ingested: 3_921_004,
      views: [
        { name: 'by-page', pageSize: 500, bucketised: 1, paginated: 1 },
        {
          name: 'by-municipality',
          pageSize: 250,
          fragmentations: [{
            type: 'ReferenceFragmentation',
            properties: {
              fragmentationPath: `${PREFIXES.adres}gemeentenaam`,
              fragmentationKey: 'municipality',
            },
          }],
          bucketised: 1,
          paginated: 1,
        },
      ],
    },
    {
      collection: 'mobility-hindrances',
      title: 'Mobility hindrances',
      description: 'Road works and events that hinder traffic (GIPOD)',
      targetClass: 'mobiliteit:Mobiliteitshinder',
      timestampPath: 'prov:generatedAtTime',
      properties: [
        { path: 'mobiliteit:beschrijving', datatype: 'xsd:string' },
        { path: 'mobiliteit:status', datatype: 'xsd:string' },
        { path: 'geo:asWKT', datatype: 'geo:wktLiteral' },
      ],
      ingested: 412_880,
      views: [
        { name: 'by-page', pageSize: 250, bucketised: 1, paginated: 1 },
        {
          name: 'recent',
          pageSize: 100,
          fragmentations: [timeBased('prov:generatedAtTime')],
          retentionPolicies: [{ type: 'DurationAgoPolicy', properties: { value: 'P30D' } }],
          bucketised: 0.082,
          paginated: 0.081,
        },
      ],
    },
    {
      collection: 'water-quality',
      title: 'Water quality observations',
      description: 'Sensor observations of surface water quality, ingested as state objects',
      targetClass: 'sosa:Observation',
      timestampPath: 'sosa:resultTime',
      createVersions: true,
      properties: [
        { path: 'sosa:hasSimpleResult', datatype: 'xsd:decimal', minCount: 1 },
        { path: 'sosa:observedProperty', minCount: 1 },
        { path: 'sosa:resultTime', datatype: 'xsd:dateTime', minCount: 1 },
      ],
      ingested: 8_450_113,
      views: [
        {
          name: 'by-time',
          pageSize: 500,
          fragmentations: [timeBased('sosa:resultTime')],
          bucketised: 0.9712,
          paginated: 0.9407,
        },
        {
          name: 'latest',
          pageSize: 250,
          retentionPolicies: [{ type: 'LatestVersionSubset', properties: { amount: '1' } }],
          bucketised: 0.0021,
          paginated: 0.0021,
        },
      ],
    },
  ],
  [IDS.staging]: [
    {
      collection: 'parcels',
      title: 'Cadastral parcels (staging)',
      description: 'Parcel changes for acceptance testing',
      targetClass: 'perceel:Perceel',
      timestampPath: 'prov:generatedAtTime',
      properties: [{ path: 'perceel:capakey', datatype: 'xsd:string', minCount: 1 }],
      ingested: 48_200,
      views: [{ name: 'by-page', pageSize: 250, bucketised: 1, paginated: 0.913 }],
    },
    {
      collection: 'mobility-hindrances',
      title: 'Mobility hindrances (staging)',
      description: 'Hindrances for acceptance testing',
      targetClass: 'mobiliteit:Mobiliteitshinder',
      timestampPath: 'prov:generatedAtTime',
      properties: [{ path: 'mobiliteit:status', datatype: 'xsd:string' }],
      ingested: 12_400,
      views: [
        { name: 'by-page', pageSize: 250, bucketised: 1, paginated: 1 },
        {
          name: 'recent',
          pageSize: 100,
          fragmentations: [timeBased('prov:generatedAtTime')],
          retentionPolicies: [{ type: 'DurationAgoPolicy', properties: { value: 'P7D' } }],
          bucketised: 0.31,
          paginated: 0.31,
        },
      ],
    },
  ],
  [IDS.sandboxServer]: [
    {
      collection: SANDBOX_EVENT_STREAM,
      title: 'Sandbox parcels',
      description: 'Try out ingesting and fragmenting parcels',
      targetClass: 'perceel:Perceel',
      timestampPath: 'prov:generatedAtTime',
      properties: [],
      ingested: 12,
      views: [
        { name: 'by-page', pageSize: 10, bucketised: 1, paginated: 1 },
        { name: 'by-time', pageSize: 10, fragmentations: [timeBased('prov:generatedAtTime')], bucketised: 1, paginated: 1 },
      ],
    },
  ],
}

// RDF ----------------------------------------------------------------------------------------

export function eventStreamTurtle(baseUrl: string, spec: StreamSpec): string {
  const stream = `<${baseUrl}/${spec.collection}>`
  const prefixes = new Set(['ldes', 'tree', 'dcterms', 'sh', spec.timestampPath.split(':')[0],
    spec.targetClass.split(':')[0]])
  const views = spec.views.map((view) => `<${baseUrl}/${spec.collection}/${view.name}>`)
  return `${prefixLines(...prefixes)}

${stream} a ldes:EventStream ;
    ldes:timestampPath ${spec.timestampPath} ;
    ldes:versionOfPath dcterms:isVersionOf ;${spec.createVersions ? '\n    ldes:createVersions true ;' : ''}
    tree:shape <${baseUrl}/${spec.collection}/shape>${views.length ? ` ;
    tree:view ${views.join(' ,\n        ')}` : ''} .

<${baseUrl}/${spec.collection}/shape> a sh:NodeShape ;
    sh:targetClass ${spec.targetClass} .
`
}

export function viewTurtle(baseUrl: string, collection: string, view: ViewSpec): string {
  const fragmentations = (view.fragmentations ?? []).map((fragmentation) => `[
            a tree:${fragmentation.type} ;
${Object.entries(fragmentation.properties).map(([key, value]) =>
    `            tree:${key} ${key === 'fragmentationPath' ? compact(value) : `"${value}"`}`).join(' ;\n')}
        ]`)
  const retention = (view.retentionPolicies ?? []).map((policy) => `
        ldes:retentionPolicy [
            a ldes:${policy.type} ;
${Object.entries(policy.properties).map(([key, value]) => key === 'amount'
    ? `            ldes:amount ${value}` : `            tree:${key} "${value}"^^xsd:duration`).join(' ;\n')}
        ] ;`).join('')
  return `${prefixLines('ldes', 'tree', 'xsd', 'dcterms', 'prov', 'geo', 'adres', 'sosa')}

<${baseUrl}/${collection}/${view.name}> a tree:Node ;
    tree:viewDescription [
        tree:fragmentationStrategy (${fragmentations.length ? ` ${fragmentations.join(' ')} ` : ''}) ;${retention}
        tree:pageSize "${view.pageSize}"^^xsd:int
    ] .
`
}

export function shapeTurtle(baseUrl: string, spec: StreamSpec): string {
  const properties = spec.properties.map((property) => `[
        sh:path ${property.path}${property.datatype ? ` ;\n        sh:datatype ${property.datatype}` : ''}${property.minCount ? ` ;\n        sh:minCount ${property.minCount}` : ''}
    ]`)
  const prefixes = new Set(['sh', 'xsd', 'geo', spec.targetClass.split(':')[0]])
  return `${prefixLines(...prefixes)}

<${baseUrl}/${spec.collection}/shape> a sh:NodeShape ;
    sh:targetClass ${spec.targetClass} ;
    sh:closed false ;
    sh:property ${properties.join(' ,\n    ')} .
`
}

/** The catalog of the server itself, the way the server expects it: a blank node */
export function serverCatalogTurtle(name: string): string {
  return `${prefixLines('dcat', 'dcterms')}

[] a dcat:Catalog ;
    dcterms:title "${name}"@en ;
    dcterms:description "Linked Data Event Streams published by ${name}"@en .
`
}

/**
 * The catalog the server serves: its own, with the datasets of the given event streams (those
 * with DCAT metadata) and a data service per view
 */
export function combinedCatalog(baseUrl: string, serverCatalog: ServerCatalog | undefined,
  streams: EventStream[]): string {
  if (!serverCatalog?.catalogId) return ''
  // The server names its catalog after its base URL
  const catalog = `<${baseUrl}>`
  const own = serverCatalog.document.replace(/\[\s*\]/, catalog).trimEnd()
  if (!streams.length) return `${own}\n`
  const services = streams.flatMap((stream) => stream.views.map((view) => ({ stream, view })))
  return `${own}

${prefixLines('dcat', 'dcterms')}

${catalog} dcat:dataset ${streams.map((stream) => `<${baseUrl}/${stream.collection}>`).join(' ,\n        ')}${services.length ? ` ;
    dcat:service ${services.map(({ stream, view }) => `<${baseUrl}/${stream.collection}/${view}>`).join(' ,\n        ')}` : ''} .
${streams.map((stream) => `
<${baseUrl}/${stream.collection}> a dcat:Dataset ;
    dcterms:title "${streamTitle(stream.collection)}"@en .`).join('\n')}
${services.map(({ stream, view }) => `
<${baseUrl}/${stream.collection}/${view}> a dcat:DataService ;
    dcterms:title "${streamTitle(stream.collection)}, ${view}"@en ;
    dcat:endpointURL <${baseUrl}/${stream.collection}/${view}> ;
    dcat:servesDataset <${baseUrl}/${stream.collection}> .`).join('\n')}
`
}

function streamTitle(collection: string): string {
  return Object.values(streamSpecs).flat().find((spec) => spec.collection === collection)?.title
    ?? collection
}

export function toEventStream(baseUrl: string, spec: StreamSpec): EventStream {
  return {
    collection: spec.collection,
    timestampPath: expand(spec.timestampPath),
    versionOfPath: `${PREFIXES.dcterms}isVersionOf`,
    createVersions: spec.createVersions ?? false,
    views: spec.views.map((view) => view.name).sort(),
    definition: eventStreamTurtle(baseUrl, spec),
  }
}

export function toView(baseUrl: string, collection: string, view: ViewSpec): View {
  return {
    collection,
    name: view.name,
    pageSize: view.pageSize,
    fragmentations: view.fragmentations ?? [],
    retentionPolicies: view.retentionPolicies ?? [],
    definition: viewTurtle(baseUrl, collection, view),
  }
}

function toStatistics(spec: StreamSpec): StreamStatistics {
  const views = spec.views.map((view) => ({
    view: view.name,
    bucketisedMembers: Math.round(spec.ingested * view.bucketised),
    paginatedMembers: Math.round(spec.ingested * view.paginated),
  }))
  return {
    collection: spec.collection,
    ingestedMembers: spec.ingested,
    backlog: backlogOf(views),
    views,
  }
}

// Instances ----------------------------------------------------------------------------------

function instances(now: Date): Instance[] {
  const ago = (days: number) => new Date(now.getTime() - days * 86_400_000).toISOString()
  return [
    {
      id: IDS.production,
      type: 'LDES_SERVER',
      name: 'Production',
      url: 'https://ldes.vlaanderen.example',
      origin: 'REGISTERED',
      sandbox: false,
      registeredAt: ago(182),
    },
    {
      id: IDS.staging,
      type: 'LDES_SERVER',
      name: 'Staging',
      url: 'https://staging.ldes.vlaanderen.example',
      origin: 'REGISTERED',
      sandbox: false,
      registeredAt: ago(96),
    },
    {
      id: IDS.sandboxServer,
      type: 'LDES_SERVER',
      name: 'Sandbox LDES server',
      url: 'http://localhost:32768',
      internalUrl: 'http://sandbox-ldes-server:8080',
      origin: 'LOCAL',
      sandbox: true,
      registeredAt: ago(0.05),
    },
    {
      id: IDS.legacy,
      type: 'LDES_SERVER',
      name: 'Legacy 3.x',
      url: 'https://legacy.ldes.example.org',
      origin: 'REGISTERED',
      sandbox: false,
      registeredAt: ago(400),
    },
    {
      id: IDS.ingestLdio,
      type: 'LDIO',
      name: 'Ingest LDIO',
      url: 'https://ingest.ldio.vlaanderen.example',
      origin: 'REGISTERED',
      sandbox: false,
      registeredAt: ago(180),
    },
    {
      id: IDS.consumerLdio,
      type: 'LDIO',
      name: 'Consumer LDIO',
      url: 'https://consumer.ldio.vlaanderen.example',
      origin: 'REGISTERED',
      sandbox: false,
      registeredAt: ago(64),
    },
    {
      id: IDS.sandboxLdio,
      type: 'LDIO',
      name: 'Sandbox LDIO',
      url: 'http://localhost:32771',
      internalUrl: 'http://sandbox-ldio:8080',
      origin: 'LOCAL',
      sandbox: true,
      registeredAt: ago(0.05),
    },
  ]
}

// Pipelines ----------------------------------------------------------------------------------

function pipelines(): Record<string, DeployedPipeline[]> {
  return {
    [IDS.ingestLdio]: [
      {
        status: 'RUNNING',
        updateSource: 'MANUAL',
        pipeline: {
          name: 'parcels-ingest',
          description: 'Polls the parcel change API and ingests version objects',
          input: {
            name: 'Ldio:HttpInPoller',
            config: { url: 'https://api.vlaanderen.example/parcels/changes', interval: 'PT1M' },
            adapter: {
              name: 'Ldio:JsonToLdAdapter',
              config: { context: '{"@vocab": "https://data.vlaanderen.be/ns/perceel#"}' },
            },
          },
          transformers: [
            { name: 'Ldio:GeoJsonToWktTransformer' },
            {
              name: 'Ldio:VersionObjectCreator',
              config: {
                'member-type': 'https://data.vlaanderen.be/ns/perceel#Perceel',
                'date-observed-property': '<https://data.vlaanderen.be/ns/perceel#lastModified>',
              },
            },
          ],
          outputs: [
            { name: 'Ldio:HttpOut', config: { endpoint: 'https://ldes.vlaanderen.example/parcels' } },
          ],
        },
      },
      {
        status: 'RUNNING',
        updateSource: 'AUTO',
        pipeline: {
          name: 'addresses-sync',
          description: 'Maps the address register export to RDF with RML',
          input: {
            name: 'Ldio:HttpInPoller',
            config: { url: 'https://api.vlaanderen.example/addresses/changes', cron: '0 */5 * * * *' },
            adapter: { name: 'Ldio:RmlAdapter', config: { mapping: '/ldio/mappings/adressen.rml.ttl' } },
          },
          transformers: [
            {
              name: 'Ldio:VersionObjectCreator',
              config: { 'member-type': 'https://data.vlaanderen.be/ns/adres#Adres' },
            },
          ],
          outputs: [
            { name: 'Ldio:HttpOut', config: { endpoint: 'https://ldes.vlaanderen.example/addresses' } },
          ],
        },
      },
      {
        status: 'HALTED',
        updateSource: 'MANUAL',
        pipeline: {
          name: 'hindrances-geo',
          description: 'Receives GIPOD hindrances, converts their geometry and skolemises them',
          input: {
            name: 'Ldio:HttpIn',
            adapter: {
              name: 'Ldio:JsonToLdAdapter',
              config: { context: '{"@vocab": "https://data.vlaanderen.be/ns/mobiliteit#"}' },
            },
          },
          transformers: [
            { name: 'Ldio:GeoJsonToWktTransformer' },
            { name: 'Ldio:SkolemisationTransformer', config: { skolemDomain: 'https://data.vlaanderen.be' } },
          ],
          outputs: [
            {
              name: 'Ldio:HttpOut',
              config: { endpoint: 'https://ldes.vlaanderen.example/mobility-hindrances' },
            },
            { name: 'Ldio:ConsoleOut', config: { 'rdf-writer': { 'content-type': 'application/n-quads' } } },
          ],
        },
      },
    ],
    [IDS.consumerLdio]: [
      {
        status: 'RUNNING',
        updateSource: 'AUTO',
        pipeline: {
          name: 'water-quality-replication',
          description: 'Replicates water quality observations into GraphDB',
          input: {
            name: 'Ldio:LdesClient',
            config: {
              urls: ['https://ldes.vlaanderen.example/water-quality/by-time'],
              state: 'postgres',
              'keep-state': 'true',
            },
          },
          transformers: [
            {
              name: 'Ldio:VersionMaterialiser',
              config: { 'versionOf-property': 'http://purl.org/dc/terms/isVersionOf' },
            },
          ],
          outputs: [
            {
              name: 'Ldio:RepositorySink',
              config: { 'sparql-host': 'http://graphdb:7200', 'repository-id': 'water-quality' },
            },
          ],
        },
      },
      {
        status: 'RUNNING',
        updateSource: 'AUTO',
        pipeline: {
          name: 'parcels-to-kafka',
          description: 'Publishes changed parcels to Kafka for downstream services',
          input: {
            name: 'Ldio:LdesClient',
            config: { urls: ['https://ldes.vlaanderen.example/parcels/by-time'] },
          },
          transformers: [{ name: 'Ldio:ChangeDetectionFilter', config: { state: 'sqlite' } }],
          outputs: [
            {
              name: 'Ldio:KafkaOut',
              config: {
                'bootstrap-servers': 'kafka:9092',
                topic: 'parcels',
                'rdf-writer': { 'content-type': 'application/ld+json' },
              },
            },
          ],
        },
      },
      {
        status: 'INIT',
        updateSource: 'AUTO',
        pipeline: {
          name: 'addresses-sparql',
          description: 'Loads the address register into Virtuoso',
          input: {
            name: 'Ldio:LdesClient',
            config: { urls: ['https://ldes.vlaanderen.example/addresses/by-municipality'] },
          },
          transformers: [
            {
              name: 'Ldio:VersionMaterialiser',
              config: { 'versionOf-property': 'http://purl.org/dc/terms/isVersionOf', 'restrict-to-members': 'true' },
            },
          ],
          outputs: [
            {
              name: 'Ldio:HttpSparqlOut',
              config: { endpoint: 'http://virtuoso:8890/sparql', graph: 'https://data.vlaanderen.be/graph/adressen' },
            },
          ],
        },
      },
    ],
    [IDS.sandboxLdio]: [sandboxIngestPipeline()],
  }
}

/** The pipeline that feeds the event stream of the sandbox, as the backend deploys it */
export function sandboxIngestPipeline(eventStreamUrl = SANDBOX_EVENT_STREAM_URL): DeployedPipeline {
  return {
    status: 'RUNNING',
    updateSource: 'AUTO',
    pipeline: {
      name: SANDBOX_INGEST_PIPELINE,
      description: `Receives members over HTTP and ingests them in the ${SANDBOX_EVENT_STREAM} event stream of the sandbox LDES server`,
      input: { name: 'Ldio:HttpIn', adapter: { name: 'Ldio:RdfAdapter', config: {} }, config: {} },
      transformers: [],
      outputs: [{ name: 'Ldio:HttpOut', config: { endpoint: eventStreamUrl } }],
    },
  }
}

function pipelineStatistics(): Record<string, PipelineStatistics[]> {
  const stats = (pipeline: string, membersIn: number, membersOut: number) =>
    ({ pipeline, membersIn, membersOut, inFlight: Math.max(0, membersIn - membersOut) })
  return {
    [IDS.ingestLdio]: [
      stats('parcels-ingest', 1_284_640, 1_284_512),
      stats('addresses-sync', 3_921_004, 3_921_004),
      stats('hindrances-geo', 412_903, 412_880),
    ],
    [IDS.consumerLdio]: [
      stats('water-quality-replication', 7_948_210, 7_948_188),
      stats('parcels-to-kafka', 1_283_955, 311_402),
      stats('addresses-sparql', 0, 0),
    ],
    [IDS.sandboxLdio]: [stats(SANDBOX_INGEST_PIPELINE, 12, 12)],
  }
}

// Background processes -----------------------------------------------------------------------

export function processes(failingRetention: boolean, busy: boolean): BackgroundProcess[] {
  return [
    { job: 'fragmentation', step: 'bucketisation', completedRuns: 2_874, failedRuns: 0, averageMillis: 412, longestMillis: 4_210, running: busy },
    { job: 'fragmentation', step: 'pagination', completedRuns: 2_874, failedRuns: 0, averageMillis: 268, longestMillis: 2_904, running: false },
    {
      job: 'maintenance', step: 'viewRetentionStep', completedRuns: failingRetention ? 5 : 6, failedRuns: failingRetention ? 1 : 0,
      averageMillis: 41_877, longestMillis: failingRetention ? 300_000 : 52_310, running: false,
    },
    { job: 'maintenance', step: 'eventSourceRetentionStep', completedRuns: 6, failedRuns: 0, averageMillis: 3_120, longestMillis: 4_088, running: false },
    { job: 'maintenance', step: 'compactionStep', completedRuns: 6, failedRuns: 0, averageMillis: 96_402, longestMillis: 120_118, running: false },
    { job: 'maintenance', step: 'deletionStep', completedRuns: 6, failedRuns: 0, averageMillis: 8_120, longestMillis: 9_870, running: false },
  ]
}

// Dashboard ----------------------------------------------------------------------------------

function seededRandom(seed: number): () => number {
  let state = seed
  return () => {
    state = (state + 0x6d2b79f5) | 0
    let t = Math.imul(state ^ (state >>> 15), 1 | state)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4_294_967_296
  }
}

function hourlySeries(now: Date, average: number, seed: number): ThroughputSeries['points'] {
  const random = seededRandom(seed)
  const end = new Date(now)
  end.setMinutes(0, 0, 0)
  return Array.from({ length: 24 }, (_, i) => {
    const time = new Date(end.getTime() - (23 - i) * 3_600_000)
    // Office hours are busier than nights
    const daily = 1 + 0.55 * Math.sin(((time.getHours() - 8) / 24) * 2 * Math.PI)
    return { time: time.toISOString(), members: Math.round(average * daily * (0.8 + 0.4 * random())) }
  })
}

function activity(now: Date): ActivityEvent[] {
  const at = (minutes: number) => new Date(now.getTime() - minutes * 60_000).toISOString()
  const production = { type: 'LDES_SERVER' as const, id: IDS.production, name: 'Production' }
  const staging = { type: 'LDES_SERVER' as const, id: IDS.staging, name: 'Staging' }
  const ingest = { type: 'LDIO' as const, id: IDS.ingestLdio, name: 'Ingest LDIO' }
  const consumer = { type: 'LDIO' as const, id: IDS.consumerLdio, name: 'Consumer LDIO' }
  return [
    {
      id: 'a1', time: at(2), severity: 'warn', source: production,
      title: 'Backlog on water-quality/by-time',
      detail: '257,729 members are waiting to be paginated',
    },
    { id: 'a2', time: at(7), severity: 'success', source: ingest, title: 'Pipeline parcels-ingest resumed' },
    {
      id: 'a3', time: at(18), severity: 'error', source: staging, title: 'Retention failed',
      detail: 'canceling statement due to statement timeout',
    },
    {
      id: 'a4', time: at(26), severity: 'info', source: consumer,
      title: 'water-quality-replication is synchronising',
      detail: 'Replication completed, following new members',
    },
    {
      id: 'a5', time: at(43), severity: 'error',
      source: { type: 'LDES_SERVER', id: IDS.legacy, name: 'Legacy 3.x' },
      title: 'Health check failed', detail: 'https://legacy.ldes.example.org is unreachable',
    },
    {
      id: 'a6', time: at(71), severity: 'info',
      source: { type: 'LDES_SERVER', id: IDS.sandboxServer, name: 'Sandbox LDES server' },
      title: 'Local LDES server started',
      detail: 'On http://localhost:32768, and on http://sandbox-ldes-server:8080 for other local instances',
    },
    {
      id: 'a7', time: at(128), severity: 'warn', source: ingest,
      title: 'Pipeline hindrances-geo halted', detail: 'Halted manually through the pipeline API',
    },
    { id: 'a8', time: at(196), severity: 'success', source: production, title: 'View water-quality/latest created' },
    { id: 'a9', time: at(310), severity: 'info', source: consumer, title: 'Pipeline addresses-sparql created' },
  ]
}

// State --------------------------------------------------------------------------------------

export function createSampleState(now = new Date()): SampleState {
  const all = instances(now)
  // A local server publishes its IRIs on its internal URL, like the real one
  const urlOf = (id: string) => {
    const instance = all.find((candidate) => candidate.id === id)!
    return instance.internalUrl ?? instance.url
  }
  const nameOf = (id: string) => all.find((instance) => instance.id === id)!.name
  const state: SampleState = {
    instances: all,
    health: Object.fromEntries(all.map((instance) => [instance.id,
      instance.id === IDS.legacy ? 'DOWN' : 'UP'])),
    problems: {
      [IDS.staging]: ['Retention failed at 00:00: canceling statement due to statement timeout'],
    },
    eventStreams: {},
    views: {},
    shapes: {},
    serverCatalogs: {},
    datasets: {},
    statistics: {},
    processes: {},
    pipelines: pipelines(),
    pipelineStatistics: pipelineStatistics(),
    clientStatuses: {
      [IDS.consumerLdio]: {
        'water-quality-replication': 'SYNCHRONISING',
        'parcels-to-kafka': 'REPLICATING',
        'addresses-sparql': 'REPLICATING',
      },
    },
    activity: activity(now),
    throughput: [
      { instanceId: IDS.production, label: 'Production', points: hourlySeries(now, 4_300, 7) },
      { instanceId: IDS.staging, label: 'Staging', points: hourlySeries(now, 640, 11) },
      { instanceId: IDS.sandboxServer, label: 'Sandbox LDES server', points: hourlySeries(now, 40, 13) },
    ],
  }
  for (const [serverId, specs] of Object.entries(streamSpecs)) {
    const url = urlOf(serverId)
    state.eventStreams[serverId] = specs.map((spec) => toEventStream(url, spec))
    state.statistics[serverId] = specs.map(toStatistics)
    state.serverCatalogs[serverId] = serverId === IDS.sandboxServer
      ? { catalogId: null, document: serverCatalogTurtle(nameOf(serverId)) }
      : { catalogId: `${serverId.slice(-4)}-catalog`, document: serverCatalogTurtle(`${nameOf(serverId)} LDES server`) }
    state.datasets[serverId] = serverId === IDS.sandboxServer ? [] : specs.map((spec) => spec.collection)
    state.processes[serverId] = processes(serverId === IDS.staging, serverId === IDS.production)
    specs.forEach((spec) => {
      state.views[`${serverId}/${spec.collection}`] = spec.views.map((view) =>
        toView(url, spec.collection, view))
      state.shapes[`${serverId}/${spec.collection}`] = shapeTurtle(url, spec)
    })
  }
  return state
}
