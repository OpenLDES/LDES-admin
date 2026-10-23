import type { HealthStatus, Instance } from '@/domain/instance'
import type {
  BackgroundProcess,
  EventStream,
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
  /** Per server */
  catalogs: Record<string, string>
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
      collection: 'sandbox-parcels',
      title: 'Sandbox parcels',
      description: 'Try out ingesting and fragmenting parcels',
      targetClass: 'perceel:Perceel',
      timestampPath: 'prov:generatedAtTime',
      properties: [{ path: 'perceel:capakey', datatype: 'xsd:string', minCount: 1 }],
      ingested: 12,
      views: [{ name: 'by-page', pageSize: 10, bucketised: 1, paginated: 1 }],
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

export function catalogTurtle(baseUrl: string, name: string, specs: StreamSpec[]): string {
  const datasets = specs.map((spec) => `<${baseUrl}/${spec.collection}>`)
  const services = specs.flatMap((spec) => spec.views.map((view) => ({ spec, view })))
  return `${prefixLines('dcat', 'dcterms')}

<${baseUrl}/catalog> a dcat:Catalog ;
    dcterms:title "${name}"@en ;
    dcterms:description "Linked Data Event Streams published by ${name}"@en${datasets.length ? ` ;
    dcat:dataset ${datasets.join(' ,\n        ')}` : ''}${services.length ? ` ;
    dcat:service ${services.map(({ spec, view }) => `<${baseUrl}/${spec.collection}/${view.name}>`).join(' ,\n        ')}` : ''} .
${specs.map((spec) => `
<${baseUrl}/${spec.collection}> a dcat:Dataset ;
    dcterms:title "${spec.title}"@en ;
    dcterms:description "${spec.description}"@en .`).join('\n')}
${services.map(({ spec, view }) => `
<${baseUrl}/${spec.collection}/${view.name}> a dcat:DataService ;
    dcterms:title "${spec.title}, ${view.name}"@en ;
    dcat:endpointURL <${baseUrl}/${spec.collection}/${view.name}> ;
    dcat:servesDataset <${baseUrl}/${spec.collection}> .`).join('\n')}
`
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
      name: 'Sandbox',
      url: 'http://localhost:32768',
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
    [IDS.sandboxLdio]: [
      {
        status: 'RUNNING',
        updateSource: 'AUTO',
        pipeline: {
          name: 'sandbox-console',
          description: 'Logs everything posted to it',
          input: { name: 'Ldio:HttpIn', adapter: { name: 'Ldio:RdfAdapter' } },
          transformers: [],
          outputs: [{ name: 'Ldio:ConsoleOut' }],
        },
      },
    ],
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
    [IDS.sandboxLdio]: [stats('sandbox-console', 3, 3)],
  }
}

// Background processes -----------------------------------------------------------------------

function processes(now: Date, failingRetention: boolean, busy: boolean): BackgroundProcess[] {
  const ago = (ms: number) => new Date(now.getTime() - ms).toISOString()
  const inMs = (ms: number) => new Date(now.getTime() + ms).toISOString()
  const midnight = new Date(now)
  midnight.setUTCHours(0, 0, 0, 0)
  const nextMidnight = new Date(midnight.getTime() + 86_400_000).toISOString()
  return [
    {
      id: 'bucketisation',
      name: 'Bucketisation',
      description: 'Assigns newly ingested members to the buckets of every view',
      icon: 'pi pi-th-large',
      status: busy ? 'RUNNING' : 'IDLE',
      schedule: '*/30 * * * * *',
      lastRun: ago(18_000),
      nextRun: inMs(12_000),
      lastDurationMs: 4_210,
      processedItems: busy ? 18_442 : 0,
      progress: busy ? 72 : undefined,
    },
    {
      id: 'pagination',
      name: 'Pagination',
      description: 'Puts bucketised members on pages and links the pages together',
      icon: 'pi pi-book',
      status: 'IDLE',
      schedule: '*/30 * * * * *',
      lastRun: ago(14_000),
      nextRun: inMs(16_000),
      lastDurationMs: 2_904,
      processedItems: busy ? 9_310 : 0,
    },
    {
      id: 'retention',
      name: 'Retention',
      description: 'Removes members that no longer match the retention policies of a view',
      icon: 'pi pi-clock',
      status: failingRetention ? 'FAILED' : 'IDLE',
      schedule: '0 0 0 * * *',
      lastRun: midnight.toISOString(),
      nextRun: nextMidnight,
      lastDurationMs: failingRetention ? 300_000 : 41_877,
      processedItems: failingRetention ? 0 : 12_044,
      message: failingRetention
        ? 'ERROR: canceling statement due to statement timeout (DELETE FROM page_members …)'
        : undefined,
    },
    {
      id: 'compaction',
      name: 'Compaction',
      description: 'Merges small immutable pages into larger ones',
      icon: 'pi pi-compress',
      status: 'IDLE',
      schedule: '0 0 0 * * *',
      lastRun: new Date(midnight.getTime() + 42_000).toISOString(),
      nextRun: nextMidnight,
      lastDurationMs: 96_402,
      processedItems: 1_380,
    },
    {
      id: 'deletion',
      name: 'Deletion',
      description: 'Deletes compacted pages once the compaction duration (P7D) has passed',
      icon: 'pi pi-trash',
      status: 'IDLE',
      schedule: '0 0 0 * * *',
      lastRun: new Date(midnight.getTime() + 140_000).toISOString(),
      nextRun: nextMidnight,
      lastDurationMs: 8_120,
      processedItems: 211,
    },
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
      source: { type: 'LDES_SERVER', id: IDS.sandboxServer, name: 'Sandbox' },
      title: 'Local LDES server started', detail: 'openldes/ldes-server:4.2.1 on port 32768',
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
  const urlOf = (id: string) => all.find((instance) => instance.id === id)!.url
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
    catalogs: {},
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
      { instanceId: IDS.sandboxServer, label: 'Sandbox', points: hourlySeries(now, 40, 13) },
    ],
  }
  for (const [serverId, specs] of Object.entries(streamSpecs)) {
    const url = urlOf(serverId)
    state.eventStreams[serverId] = specs.map((spec) => toEventStream(url, spec))
    state.statistics[serverId] = specs.map(toStatistics)
    state.catalogs[serverId] = catalogTurtle(url, `${nameOf(serverId)} LDES server`, specs)
    state.processes[serverId] = processes(now, serverId === IDS.staging, serverId === IDS.production)
    specs.forEach((spec) => {
      state.views[`${serverId}/${spec.collection}`] = spec.views.map((view) =>
        toView(url, spec.collection, view))
      state.shapes[`${serverId}/${spec.collection}`] = shapeTurtle(url, spec)
    })
  }
  return state
}
