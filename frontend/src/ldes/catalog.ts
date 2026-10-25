import { PREFIXES } from '@/domain/rdf'
import { RDF_CONTENT_TYPES, type ConfigProperty } from '@/pipeline/catalog'

/**
 * What can be configured on event streams and views of an LDES server, with what the forms need
 * to edit and validate it: the predicates, their values and the kinds of fragmentations and
 * retention policies.
 *
 * Source: https://openldes.github.io/LDESServer/4.2.1/ and the SHACL shapes the admin API
 * validates with; checked against LDES server 4.2.1 where they disagree.
 */
export interface RdfProperty extends ConfigProperty {
  /** IRI of the predicate; the key is its prefixed name, e.g. tree:maxGranularity */
  predicate: string
  /** The datatype of the literal value; properties without one hold an IRI */
  datatype?: string
  /** The server only accepts that datatype (sh:datatype), e.g. xsd:integer and not xsd:int */
  strictDatatype?: boolean
  /** For `integer`: the highest value */
  max?: number
  /** For `string`: the value is a regular expression */
  regex?: boolean
}

/** A kind of fragmentation or retention policy */
export interface ResourceKind {
  /** IRI of its rdf:type */
  type: string
  label: string
  description: string
  icon: string
  docs: string
  properties: RdfProperty[]
  /** Values for a new one, by key; they may build on the event stream */
  initial: (stream: StreamContext) => Record<string, string>
}

/** What a view may build on: the paths of its event stream */
export interface StreamContext {
  timestampPath?: string
  versionOfPath?: string
}

const { rdf, ldes, tree, xsd, dcterms, prov, geo } = PREFIXES
const DOCS = 'https://openldes.github.io/LDESServer/4.2.1'

const fragmentationPath: Omit<RdfProperty, 'description'> = {
  key: 'tree:fragmentationPath',
  predicate: `${tree}fragmentationPath`,
  type: 'iri',
}

const subjectFilter = {
  type: 'string',
  datatype: `${xsd}string`,
  regex: true,
  example: '.*/addresses/.*',
  description: 'Regular expression: only use values of subjects whose IRI matches',
} as const

export const fragmentationKinds: ResourceKind[] = [
  {
    type: `${tree}HierarchicalTimeBasedFragmentation`,
    label: 'Time-based',
    description: 'Buckets per year, within them per month, and so on',
    icon: 'pi pi-calendar',
    docs: `${DOCS}/configuration/fragmentations/timebased`,
    properties: [
      {
        key: 'tree:maxGranularity',
        predicate: `${tree}maxGranularity`,
        type: 'select',
        options: ['year', 'month', 'day', 'hour', 'minute', 'second'].map((value) => ({ value })),
        datatype: `${xsd}string`,
        strictDatatype: true,
        required: true,
        description: 'The smallest buckets: years hold months, months hold days, and so on down to this unit',
      },
      {
        ...fragmentationPath,
        required: true,
        example: 'prov:generatedAtTime',
        description: 'The property that holds the time of a member, an xsd:dateTime',
      },
      // The admin API documents fragmenterSubjectFilter and validates fragmentSubjectFilter, but
      // the fragmentation reads this one
      { key: 'tree:fragmentationSubjectFilter', predicate: `${tree}fragmentationSubjectFilter`, ...subjectFilter },
    ],
    initial: (stream) => ({
      'tree:maxGranularity': 'day',
      'tree:fragmentationPath': stream.timestampPath ?? `${prov}generatedAtTime`,
    }),
  },
  {
    type: `${tree}GeospatialFragmentation`,
    label: 'Geospatial',
    description: 'A bucket per map tile a geometry of the member touches',
    icon: 'pi pi-map',
    docs: `${DOCS}/configuration/fragmentations/geospatial`,
    properties: [
      {
        key: 'tree:maxZoom',
        predicate: `${tree}maxZoom`,
        type: 'integer',
        datatype: `${xsd}integer`,
        strictDatatype: true,
        min: 0,
        max: 23,
        required: true,
        example: '15',
        description: 'Zoom level of the tiles, from 0 (the whole world) to 23; at 15 a tile spans about a kilometre',
      },
      {
        ...fragmentationPath,
        required: true,
        example: 'geo:asWKT',
        description: 'The property that holds the geometry of a member, a WKT literal',
      },
      { key: 'tree:fragmenterSubjectFilter', predicate: `${tree}fragmenterSubjectFilter`, ...subjectFilter },
    ],
    initial: () => ({ 'tree:maxZoom': '15', 'tree:fragmentationPath': `${geo}asWKT` }),
  },
  {
    type: `${tree}ReferenceFragmentation`,
    label: 'Reference',
    description: 'A bucket per value of a property, e.g. per state object',
    icon: 'pi pi-tag',
    docs: `${DOCS}/configuration/fragmentations/reference`,
    properties: [
      {
        ...fragmentationPath,
        default: 'rdf:type',
        description: 'The property whose values make the buckets',
      },
      {
        key: 'tree:fragmentationKey',
        predicate: `${tree}fragmentationKey`,
        type: 'string',
        datatype: `${xsd}string`,
        strictDatatype: true,
        default: 'reference',
        pattern: { regex: /^[\w.~-]+$/, message: 'Use letters, digits, -, _, . or ~: it becomes a query parameter' },
        description: 'Name of the query parameter in the URL of a bucket, e.g. ?version=…',
      },
    ],
    initial: (stream) => ({
      'tree:fragmentationPath': stream.versionOfPath ?? `${dcterms}isVersionOf`,
      'tree:fragmentationKey': 'version',
    }),
  },
]

export const retentionPolicyKinds: ResourceKind[] = [
  {
    type: `${ldes}DurationAgoPolicy`,
    label: 'Time-based',
    description: 'Keeps members whose timestamp is less than a duration ago',
    icon: 'pi pi-clock',
    docs: `${DOCS}/configuration/retention-policies/timebased`,
    properties: [
      {
        key: 'tree:value',
        predicate: `${tree}value`,
        type: 'duration',
        datatype: `${xsd}duration`,
        strictDatatype: true,
        required: true,
        example: 'P30D',
        description: 'How long members are kept, as an xsd:duration, e.g. P30D, PT12H or P1Y',
      },
    ],
    initial: () => ({ 'tree:value': 'P30D' }),
  },
  {
    type: `${ldes}LatestVersionSubset`,
    label: 'Version-based',
    description: 'Keeps the latest versions of every state object',
    icon: 'pi pi-history',
    docs: `${DOCS}/configuration/retention-policies/version-based`,
    properties: [
      {
        key: 'ldes:amount',
        predicate: `${ldes}amount`,
        type: 'integer',
        datatype: `${xsd}integer`,
        strictDatatype: true,
        min: 1,
        required: true,
        example: '1',
        description: 'How many versions of each state object are kept',
      },
    ],
    initial: () => ({ 'ldes:amount': '1' }),
  },
]

export function fragmentationKind(type: string | undefined): ResourceKind | undefined {
  return fragmentationKinds.find((kind) => kind.type === type)
}

export function retentionPolicyKind(type: string | undefined): ResourceKind | undefined {
  return retentionPolicyKinds.find((kind) => kind.type === type)
}

// Event streams -------------------------------------------------------------------------------

export const EVENT_STREAM_DOCS = `${DOCS}/configuration/event-stream`

export const eventStreamProperties: RdfProperty[] = [
  {
    key: 'ldes:timestampPath',
    predicate: `${ldes}timestampPath`,
    type: 'select',
    custom: true,
    options: [
      { value: 'dcterms:created', label: 'Created' },
      { value: 'dcterms:modified', label: 'Modified' },
      { value: 'prov:generatedAtTime', label: 'Generated at' },
      { value: 'sosa:resultTime', label: 'Result time' },
    ],
    required: true,
    description: 'The property that holds the time of a member; time-based views and retention use it',
  },
  {
    key: 'ldes:versionOfPath',
    predicate: `${ldes}versionOfPath`,
    type: 'select',
    custom: true,
    options: [{ value: 'dcterms:isVersionOf', label: 'Is version of' }],
    required: true,
    description: 'The property that refers to the state object a member is a version of',
  },
  {
    key: 'ldes:createVersions',
    predicate: `${ldes}createVersions`,
    type: 'boolean',
    datatype: `${xsd}boolean`,
    strictDatatype: true,
    default: 'false',
    description: 'Ingest state objects: the server turns each one into a version object',
  },
  {
    key: 'ldes:versionDelimiter',
    predicate: `${ldes}versionDelimiter`,
    type: 'string',
    datatype: `${xsd}string`,
    strictDatatype: true,
    default: '/',
    description: 'Joins the IRI of the state object and the timestamp into the IRI of a version',
    when: { key: 'ldes:createVersions', values: ['true'] },
  },
  {
    key: 'ldes:skolemizationDomain',
    predicate: `${ldes}skolemizationDomain`,
    type: 'url',
    example: 'https://example.org',
    description: 'Replaces the blank nodes of members by IRIs in this domain, …/.well-known/genid/…',
  },
]

/** Where the event stream reads its members from instead of HTTP (needs the Kafka ingest module) */
export const kafkaSourceProperties: RdfProperty[] = [
  {
    key: 'ldes:topic',
    predicate: `${ldes}topic`,
    type: 'string',
    datatype: `${xsd}string`,
    required: true,
    example: 'parcels',
    description: 'The Kafka topic to read members from',
  },
  {
    key: 'ldes:mimeType',
    predicate: `${ldes}mimeType`,
    type: 'select',
    custom: true,
    options: [...RDF_CONTENT_TYPES, { value: 'application/json', label: 'JSON' }],
    datatype: `${xsd}string`,
    required: true,
    description: 'The content type of every message on the topic',
  },
]

/** The predicates of an event stream in the order the definition lists them */
export const EVENT_STREAM_ORDER = [
  rdf + 'type',
  ...eventStreamProperties.map((property) => property.predicate),
  `${ldes}eventSource`,
  `${ldes}kafkaSource`,
  `${tree}shape`,
]

// Views ---------------------------------------------------------------------------------------

export const VIEW_DOCS = `${DOCS}/configuration/view`

export const viewDescriptionProperties: RdfProperty[] = [
  {
    key: 'tree:pageSize',
    predicate: `${tree}pageSize`,
    type: 'integer',
    datatype: `${xsd}int`,
    min: 1,
    default: '100',
    description: 'How many members a page holds before the server starts the next one',
  },
]

/** The predicates of a view description in the order the definition lists them */
export const VIEW_DESCRIPTION_ORDER = [
  rdf + 'type',
  `${tree}fragmentationStrategy`,
  `${ldes}retentionPolicy`,
  `${tree}pageSize`,
]
