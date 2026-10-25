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

/** The catalog of an LDES server itself, without the datasets and data services it adds */
export interface ServerCatalog {
  /** The id the server gave the catalog; null when it has none yet */
  catalogId: string | null
  /** Turtle, ready to edit */
  document: string
}

/**
 * A step of a background job of the LDES server, e.g. step pagination of job fragmentation, as
 * its batch metrics tell since the server started.
 */
export interface BackgroundProcess {
  job: string
  step: string
  completedRuns: number
  failedRuns: number
  /** Average duration of a completed run; 0 when there was none */
  averageMillis: number
  /** Duration of the longest recent completed run */
  longestMillis: number
  running: boolean
}

const processInfos: Record<string, { name: string; description: string; icon: string }> = {
  bucketisation: {
    name: 'Bucketisation',
    description: 'Assigns newly ingested members to the buckets of every view',
    icon: 'pi pi-th-large',
  },
  pagination: {
    name: 'Pagination',
    description: 'Puts bucketised members on pages and links the pages together',
    icon: 'pi pi-book',
  },
  viewRetentionStep: {
    name: 'View retention',
    description: 'Removes members from views whose retention policy no longer keeps them',
    icon: 'pi pi-clock',
  },
  eventSourceRetentionStep: {
    name: 'Event source retention',
    description: 'Removes members that the retention policy of the event source no longer keeps',
    icon: 'pi pi-history',
  },
  compactionStep: {
    name: 'Compaction',
    description: 'Merges small immutable pages into larger ones',
    icon: 'pi pi-compress',
  },
  deletionStep: {
    name: 'Deletion',
    description: 'Deletes compacted pages once the compaction duration has passed',
    icon: 'pi pi-trash',
  },
}

/** Worker steps that process one view or event source on behalf of a step above */
const processAliases: Record<string, string> = {
  singleViewRetentionStep: 'viewRetentionStep',
  singleEventSourceRetentionStep: 'eventSourceRetentionStep',
  singleViewCompactionStep: 'compactionStep',
}

/** How to present a background process, e.g. "View retention" for viewRetentionStep */
export function processInfo(process: Pick<BackgroundProcess, 'step'>):
  { name: string; description?: string; icon: string } {
  const known = processInfos[processAliases[process.step] ?? process.step]
  if (known) return known
  const words = process.step.replace(/Step$/, '').replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
  return { name: words.charAt(0).toUpperCase() + words.slice(1), icon: 'pi pi-cog' }
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
  path?: string | null
  value?: string | null
  /** IRI of the fragment it links to, as published by the server */
  node: string
  remainingItems?: number | null
}

const TIME_PARAMETERS = ['year', 'month', 'day', 'hour', 'minute', 'second']

/**
 * A short name for the fragment a relation links to, derived from its IRI: "Page 2", "2026-10-24"
 * for a time bucket, "Tile 15/16812/10970" for a map tile, or the value of a reference.
 */
export function relationLabel(relation: Pick<Relation, 'node' | 'value'>): string {
  let url: URL
  try {
    url = new URL(relation.node)
  } catch {
    return relation.value ?? relation.node
  }
  const bucket = [...url.searchParams].filter(([key]) => key !== 'pageNumber')
  const page = url.searchParams.get('pageNumber')
  let label: string | undefined
  if (bucket.length && bucket.every(([key]) => TIME_PARAMETERS.includes(key))) {
    const [year, month, day, hour, minute] = TIME_PARAMETERS.map((key) => url.searchParams.get(key))
    const pad = (value: string) => value.padStart(2, '0')
    label = [year, month && pad(month), day && pad(day)].filter(Boolean).join('-')
    if (hour) label += ` ${pad(hour)}:${minute ? pad(minute) : '00'}`
  } else if (bucket.length) {
    label = bucket.map(([key, value]) => key === 'tile' ? `Tile ${value}` : value).join(' / ')
  }
  // A bucket links to its own pages as well as to smaller buckets
  if (label && page) return `${label} · page ${page}`
  if (label) return label
  if (page) return `Page ${page}`
  return relation.value ?? url.pathname.split('/').pop() ?? relation.node
}


export interface Member {
  id: string
  isVersionOf?: string
  timestamp?: string
  content: string
}
