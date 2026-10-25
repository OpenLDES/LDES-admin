import type { HealthStatus, Instance, InstanceHealth, InstanceType } from './instance'
import type { StreamStatistics } from './ldes'
import type { PipelineStatus } from './ldio'

/**
 * Everything the dashboard shows. Mirrors the dashboard web model of the backend
 * (adapter/in/rest/dashboard).
 */
export interface DashboardOverview {
  ldesServers: LdesServerSummary[]
  ldios: LdioSummary[]
}

export interface LdesServerSummary {
  server: Instance
  health: InstanceHealth
  ingestedMembers: number
  streams: StreamStatistics[]
  problems: string[]
}

export interface LdioSummary {
  ldio: Instance
  health: InstanceHealth
  pipelinesByStatus: Partial<Record<PipelineStatus, number>>
  processedMembers: number
  pipelines: PipelineSummary[]
  problems: string[]
}

export interface PipelineSummary {
  name: string
  status: PipelineStatus
  membersIn: number
  membersOut: number
}

/** Members ingested per hour by an LDES server, as sampled by LDES-admin */
export interface ThroughputSeries {
  instanceId: string
  label: string
  /** Per hour, oldest first; hours before LDES-admin started sampling are left out */
  points: { time: string; members: number }[]
}

export type ActivitySeverity = 'info' | 'success' | 'warn' | 'error'

/** Something that happened to an instance: an action through LDES-admin, or a change it noticed */
export interface ActivityEvent {
  id: string
  time: string
  severity: ActivitySeverity
  /** The name is the one the instance had at the time; it may be gone by now */
  source: { type: InstanceType; id: string; name: string }
  title: string
  detail?: string | null
}

export const healthSeverity: Record<HealthStatus, 'success' | 'danger' | 'secondary'> = {
  UP: 'success',
  DOWN: 'danger',
  UNKNOWN: 'secondary',
}
