/**
 * Event streams, views and their statistics on an LDES server. Mirrors the LDES web models of the
 * backend (adapter/in/rest/ldes).
 */
export type RdfFormat =
  | 'text/turtle'
  | 'application/ld+json'
  | 'application/n-quads'
  | 'application/n-triples'

export const rdfFormats: { label: string; value: RdfFormat }[] = [
  { label: 'Turtle', value: 'text/turtle' },
  { label: 'JSON-LD', value: 'application/ld+json' },
  { label: 'N-Quads', value: 'application/n-quads' },
  { label: 'N-Triples', value: 'application/n-triples' },
]

export interface EventStream {
  collection: string
  timestampPath?: string
  versionOfPath?: string
  createVersions: boolean
  views: string[]
  /** The event stream in Turtle, as returned by the LDES server */
  definition: string
}

/** A fragmentation strategy or retention policy */
export interface TypedConfig {
  type: string
  properties: Record<string, string>
}

export interface View {
  collection: string
  name: string
  pageSize: number
  fragmentations: TypedConfig[]
  retentionPolicies: TypedConfig[]
  definition: string
}

export interface ViewStatistics {
  view: string
  bucketisedMembers: number
  paginatedMembers: number
}

export interface StreamStatistics {
  collection: string
  ingestedMembers: number
  /** Members bucketised but not yet paginated, in the slowest view */
  backlog: number
  views: ViewStatistics[]
}

/** Members bucketised but not yet paginated, in the slowest view (as the backend computes it) */
export function backlogOf(views: ViewStatistics[]): number {
  return Math.max(0, ...views.map((view) => view.bucketisedMembers - view.paginatedMembers))
}

/** Share of the bucketised members that made it onto a page, in the slowest view */
export function paginatedShare(statistics: StreamStatistics | undefined): number {
  const shares = (statistics?.views ?? [])
    .filter((view) => view.bucketisedMembers > 0)
    .map((view) => view.paginatedMembers / view.bucketisedMembers)
  return shares.length ? Math.min(...shares) : 1
}

/** What a piece of DCAT metadata describes */
export type DcatTarget =
  | { kind: 'server'; catalogId?: string }
  | { kind: 'eventStream'; collection: string }
  | { kind: 'view'; collection: string; view: string }

// ---------------------------------------------------------------------------------------------
// Not served by the backend yet: shown with sample data until the endpoints exist.
// ---------------------------------------------------------------------------------------------

export type ProcessStatus = 'IDLE' | 'RUNNING' | 'FAILED'

/** A background job of the LDES server: fragmentation, compaction, retention, ... */
export interface BackgroundProcess {
  id: string
  name: string
  description: string
  icon: string
  status: ProcessStatus
  schedule: string
  lastRun?: string
  nextRun?: string
  lastDurationMs?: number
  processedItems?: number
  /** 0-100 while running */
  progress?: number
  message?: string
}

/** A page of an LDES view, as served by the fetch API */
export interface Fragment {
  url: string
  collection: string
  view: string
  root: boolean
  immutable: boolean
  relations: Relation[]
  members: Member[]
}

export interface Relation {
  /** Local name of the tree relation type, e.g. GreaterThanOrEqualToRelation */
  type: string
  path?: string
  value?: string
  node: string
  label: string
  remainingItems?: number
}

export interface Member {
  id: string
  isVersionOf?: string
  timestamp?: string
  content: string
}
