/**
 * Pipelines on an LDIO. Mirrors the LDIO web models of the backend (adapter/in/rest/ldio), which
 * use the same structure as the LDIO pipeline API.
 */
export type PipelineStatus =
  | 'INIT'
  | 'STARTING'
  | 'RESUMING'
  | 'RUNNING'
  | 'HALTED'
  | 'STOPPING'
  | 'STOPPED'

export type StatusChangeSource = 'AUTO' | 'MANUAL'

/** Values are strings, lists or nested objects (e.g. rdf-writer.content-type) */
export type ComponentConfig = Record<string, unknown>

export interface ComponentDefinition {
  name: string
  config?: ComponentConfig
}

export interface InputDefinition extends ComponentDefinition {
  adapter?: ComponentDefinition
}

export interface Pipeline {
  name: string
  description?: string
  input: InputDefinition
  transformers?: ComponentDefinition[]
  outputs?: ComponentDefinition[]
}

export interface DeployedPipeline {
  pipeline: Pipeline
  status: PipelineStatus
  updateSource?: StatusChangeSource
}

export interface PipelineStatistics {
  pipeline: string
  membersIn: number
  membersOut: number
  inFlight: number
}

export interface ComponentCatalog {
  inputs: string[]
  adapters: string[]
  transformers: string[]
  outputs: string[]
}

/** Only a running pipeline can be paused, and only a halted one resumed */
export const canBeHalted = (status: PipelineStatus) => status === 'RUNNING'
export const canBeResumed = (status: PipelineStatus) => status === 'HALTED'

/** Progress of a pipeline with an LDES client input */
export type LdesClientStatus = 'REPLICATING' | 'SYNCHRONISING' | 'COMPLETED' | 'ERROR'
