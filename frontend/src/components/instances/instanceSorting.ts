import type { LdesServerSummary, LdioSummary } from '@/domain/dashboard'
import type { InstanceType } from '@/domain/instance'

export type InstanceSortValue =
  | 'name-asc'
  | 'name-desc'
  | 'streams-asc'
  | 'streams-desc'
  | 'views-asc'
  | 'views-desc'
  | 'members-asc'
  | 'members-desc'
  | 'running-asc'
  | 'running-desc'
  | 'halted-asc'
  | 'halted-desc'
  | 'processed-asc'
  | 'processed-desc'

interface SortDefinition {
  label: string
  value: InstanceSortValue
  metric: 'name' | 'streams' | 'views' | 'members' | 'running' | 'halted' | 'processed'
  direction: 1 | -1
}

export interface InstanceSortOption {
  label: string
  value: InstanceSortValue
}

const SORT_DEFINITIONS: Record<InstanceType, readonly SortDefinition[]> = {
  LDES_SERVER: [
    { label: 'Name (A–Z)', value: 'name-asc', metric: 'name', direction: 1 },
    { label: 'Name (Z–A)', value: 'name-desc', metric: 'name', direction: -1 },
    { label: 'Streams (ascending)', value: 'streams-asc', metric: 'streams', direction: 1 },
    { label: 'Streams (descending)', value: 'streams-desc', metric: 'streams', direction: -1 },
    { label: 'Views (ascending)', value: 'views-asc', metric: 'views', direction: 1 },
    { label: 'Views (descending)', value: 'views-desc', metric: 'views', direction: -1 },
    { label: 'Members (ascending)', value: 'members-asc', metric: 'members', direction: 1 },
    { label: 'Members (descending)', value: 'members-desc', metric: 'members', direction: -1 },
  ],
  LDIO: [
    { label: 'Name (A–Z)', value: 'name-asc', metric: 'name', direction: 1 },
    { label: 'Name (Z–A)', value: 'name-desc', metric: 'name', direction: -1 },
    { label: 'Running pipelines (ascending)', value: 'running-asc', metric: 'running', direction: 1 },
    { label: 'Running pipelines (descending)', value: 'running-desc', metric: 'running', direction: -1 },
    { label: 'Halted pipelines (ascending)', value: 'halted-asc', metric: 'halted', direction: 1 },
    { label: 'Halted pipelines (descending)', value: 'halted-desc', metric: 'halted', direction: -1 },
    { label: 'Processed members (ascending)', value: 'processed-asc', metric: 'processed', direction: 1 },
    { label: 'Processed members (descending)', value: 'processed-desc', metric: 'processed', direction: -1 },
  ],
}

const STORAGE_PREFIX = 'ldes-admin.instances.sort.'
type InstanceSummary = LdesServerSummary | LdioSummary

export function instanceSortOptions(type: InstanceType): InstanceSortOption[] {
  return SORT_DEFINITIONS[type].map(({ label, value }) => ({ label, value }))
}

function isSortValue(type: InstanceType, value: string): value is InstanceSortValue {
  return SORT_DEFINITIONS[type].some((option) => option.value === value)
}

function storageAvailable(): Storage | undefined {
  return typeof localStorage === 'undefined' ? undefined : localStorage
}

export function readInstanceSort(
  type: InstanceType,
  storage: Storage | undefined = storageAvailable(),
): InstanceSortValue {
  const saved = storage?.getItem(`${STORAGE_PREFIX}${type}`)
  return saved && isSortValue(type, saved) ? saved : 'name-asc'
}

export function saveInstanceSort(
  type: InstanceType,
  value: InstanceSortValue,
  storage: Storage | undefined = storageAvailable(),
): void {
  if (storage && isSortValue(type, value)) storage.setItem(`${STORAGE_PREFIX}${type}`, value)
}

function metricOf(summary: InstanceSummary, metric: SortDefinition['metric']): number | string {
  if (metric === 'name') return 'server' in summary ? summary.server.name : summary.ldio.name

  if ('server' in summary) {
    if (metric === 'streams') return summary.streams.length
    if (metric === 'views') return summary.streams.reduce((count, stream) => count + stream.views.length, 0)
    if (metric === 'members') return summary.ingestedMembers
  } else {
    if (metric === 'running') return summary.pipelines.filter((pipeline) => pipeline.status === 'RUNNING').length
    if (metric === 'halted') return summary.pipelines.filter((pipeline) => pipeline.status === 'HALTED').length
    if (metric === 'processed') return summary.processedMembers
  }

  return 0
}

export function sortInstanceSummaries(
  summaries: readonly InstanceSummary[],
  type: InstanceType,
  value: InstanceSortValue,
): InstanceSummary[] {
  const option = SORT_DEFINITIONS[type].find((candidate) => candidate.value === value)
    ?? SORT_DEFINITIONS[type][0]
  return [...summaries].sort((left, right) => {
    const leftMetric = metricOf(left, option.metric)
    const rightMetric = metricOf(right, option.metric)
    const order = typeof leftMetric === 'string'
      ? leftMetric.localeCompare(String(rightMetric))
      : leftMetric - Number(rightMetric)
    return order * option.direction
  })
}
