import type { Pipeline } from '@/domain/ldio'
import { prefixLines } from '@/domain/rdf'

/**
 * A complete demo: an event stream with its views, the LDIO pipeline that feeds it, and members to
 * start from. LDES-admin creates it on request, when starting a local instance or creating an
 * event stream.
 */
export interface DemoExample {
  id: 'sensors' | 'parking'
  /** Short name, e.g. among the templates of a new event stream */
  title: string
  /** What starting a local instance offers to create */
  offer: string
  description: string
  icon: string
  /** The name of its event stream, unless the user picks another one */
  collection: string
  /** The type of its members, as the shape targets it */
  memberClass: string
  timestampPath: string
  /** The definition of the event stream, in Turtle */
  eventStream(serverUrl: string, collection: string): string
  /** The pipeline that feeds the event stream, posting members to the given URL */
  pipeline(collection: string, eventStreamUrl: string): Pipeline
  /** A member as the pipeline ingests it: a version object, in Turtle */
  member(index: number, time: Date): ExampleMember
  /** Members to start with, for an example whose pipeline does not fetch them itself */
  sampleData?: SampleData
}

export interface ExampleMember {
  id: string
  isVersionOf: string
  content: string
}

export interface SampleData {
  /** How many members, of what: e.g. "36 observations" */
  description: string
  /** The members, in Turtle, to ingest directly in the event stream */
  members(now: Date): string[]
  /** The same members as one message for the pipeline */
  message(now: Date): { content: string; contentType: string }
}

/** Every example has a paginated view and a time-based one */
export const EXAMPLE_VIEWS = ['by-page', 'by-time']

const PAGE_SIZE = 10

/** The views of an example on the event stream at the given URL, by name, in Turtle */
export function exampleViews(streamUrl: string, timestampPath: string): { name: string; definition: string }[] {
  const prefixes = `${prefixLines('tree', 'xsd')}
@prefix server:     <${streamUrl}/> .`
  return [
    {
      name: 'by-page',
      definition: `${prefixes}

server:by-page tree:viewDescription [
    tree:fragmentationStrategy () ;
    tree:pageSize "${PAGE_SIZE}"^^xsd:int
] .
`,
    },
    {
      name: 'by-time',
      definition: `${prefixes}

server:by-time tree:viewDescription [
    tree:fragmentationStrategy ( [
        a tree:HierarchicalTimeBasedFragmentation ;
        tree:maxGranularity "hour" ;
        tree:fragmentationPath <${timestampPath}>
    ] ) ;
    tree:pageSize "${PAGE_SIZE}"^^xsd:int
] .
`,
    },
  ]
}

/** The pipeline that feeds the event stream of an example */
export function examplePipelineName(collection: string): string {
  return `${collection}-ingest`
}

/** A time as the members carry it, without milliseconds: 2026-10-25T10:00:00Z */
export function isoSeconds(time: Date): string {
  return time.toISOString().replace(/\.\d{3}Z$/, 'Z')
}
