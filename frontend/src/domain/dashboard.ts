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

// ---------------------------------------------------------------------------------------------
// Not served by the backend yet: shown with sample data until the endpoints exist.
// ---------------------------------------------------------------------------------------------

export interface ThroughputSeries {
  instanceId: string
  label: string
  /** Members ingested per hour, oldest first */
  points: { time: string; members: number }[]
}

export type ActivitySeverity = 'info' | 'success' | 'warn' | 'error'

export interface ActivityEvent {
  id: string
  time: string
  severity: ActivitySeverity
  source: { type: InstanceType; id: string; name: string }
  title: string
  detail?: string
}

export const healthSeverity: Record<HealthStatus, 'success' | 'danger' | 'secondary'> = {
  UP: 'success',
  DOWN: 'danger',
  UNKNOWN: 'secondary',
}
