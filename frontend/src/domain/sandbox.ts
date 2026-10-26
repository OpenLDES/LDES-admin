import type { Instance } from './instance'

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
