import type { Instance } from './instance'
import type { Pipeline } from './ldio'

/**
 * The local LDES server and LDIO to experiment with. Mirrors the sandbox web model of the backend
 * (adapter/in/rest/sandbox).
 *
 * They run in one Docker network, so the LDIO reaches the LDES server on its internal URL: out of
 * the box, the ingest pipeline of the LDIO feeds the event stream of the LDES server.
 */
export type SandboxState = 'STOPPED' | 'PARTIAL' | 'RUNNING'

export interface Sandbox {
  state: SandboxState
  ldesServer: Instance | null
  ldio: Instance | null
  /** Name of the event stream on the sandbox LDES server, e.g. sandbox-parcels */
  eventStream: string
  /** Name of the LDIO pipeline that feeds the event stream, e.g. sandbox-ingest */
  ingestPipeline: string
  /** The event stream on the URL the LDIO reaches it, e.g. http://sandbox-ldes-server:8080/sandbox-parcels */
  eventStreamUrl: string | null
}

export const SANDBOX_EVENT_STREAM = 'sandbox-parcels'
export const SANDBOX_INGEST_PIPELINE = 'sandbox-ingest'
/** Where pipelines in the sandbox reach the event stream: the host name of the sandbox LDES server */
export const SANDBOX_EVENT_STREAM_URL = 'http://sandbox-ldes-server:8080/sandbox-parcels'

/**
 * A URL to fetch the way the HTTP In Poller of an LDIO pipeline does, to simulate the pipeline with
 * what it would receive. Mirrors the poll request of the backend.
 */
export interface PollRequest {
  url: string
  /** Headers to send along, e.g. the one with the API key */
  headers?: Record<string, string>
  /** Get an OAuth2 access token first */
  clientCredentials?: ClientCredentials
}

export interface ClientCredentials {
  tokenEndpoint: string
  clientId: string
  clientSecret: string
  scope?: string
}

export interface PolledContent {
  /** The Content-Type header as received, e.g. application/json; charset=utf-8 */
  contentType: string
  body: string
}

/**
 * What the input of a pipeline receives in a run in the sandbox. A run and its steps mirror the
 * pipeline run of the backend, which runs the LDI components LDIO runs; demo mode approximates
 * them in the browser (pipeline/simulator.ts).
 */
export interface SimulationInput {
  content: string
  contentType: string
  /** The URL it was polled from, if it was */
  source?: string
}

export type StepKind = 'input' | 'adapter' | 'transformer' | 'output'
export type StepStatus = 'ok' | 'filtered' | 'error'
export type PayloadFormat = 'turtle' | 'json' | 'text'

/** What one component did in a run */
export interface SimulationStep {
  kind: StepKind
  /** Position among the components of the same kind */
  index: number
  component: string
  status: StepStatus
  summary: string
  /** What it made: a model each (adapters and transformers can make several), or what an output sends */
  outputs: string[]
  /** Of the outputs, e.g. text/turtle, so they can be converted */
  contentType: string
  format: PayloadFormat
  durationMs: number
}

export interface SimulationResult {
  steps: SimulationStep[]
  /** Whether models reached the outputs */
  delivered: boolean
  /** The models that reached the outputs, in Turtle */
  members: string[]
}

export interface PipelineRunRequest {
  pipeline: Pipeline
  input: SimulationInput
  /** Identifies the runs of the pipeline being designed: the change detection filter remembers across them */
  session: string
  /** Whether outputs really send what they make; otherwise a dry run shows what they would send */
  executeOutputs: boolean
}
