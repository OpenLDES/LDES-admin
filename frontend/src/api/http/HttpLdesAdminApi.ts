import type {
  Instance,
  InstanceHealth,
  InstanceType,
  RegisterInstanceRequest,
  StartLocalInstanceRequest,
} from '@/domain/instance'
import type {
  BackgroundProcess,
  DcatTarget,
  EventStream,
  Fragment,
  RdfFormat,
  ServerCatalog,
  StreamStatistics,
  View,
} from '@/domain/ldes'
import type {
  ComponentCatalog,
  DeployedPipeline,
  LdesClientStatus,
  Pipeline,
  PipelineStatistics,
  PipelineStatus,
} from '@/domain/ldio'
import type { ActivityEvent, DashboardOverview, ThroughputSeries } from '@/domain/dashboard'
import type { PolledContent, PollRequest, Sandbox } from '@/domain/sandbox'
import { ApiError, type LdesAdminApi } from '../LdesAdminApi'

const TURTLE = 'text/turtle'

interface RequestOptions {
  method?: string
  /** Sent as JSON, unless a content type is given */
  body?: unknown
  contentType?: string
  accept?: string
  query?: Record<string, string | undefined>
}

/**
 * The LDES-admin REST API, over HTTP. In development, Vite proxies /api to the backend.
 */
export class HttpLdesAdminApi implements LdesAdminApi {
  constructor(private readonly baseUrl = '/api/v1') {
  }

  // Dashboard ----------------------------------------------------------------------------------

  getDashboard(): Promise<DashboardOverview> {
    return this.json('/dashboard')
  }

  getThroughput(): Promise<ThroughputSeries[]> {
    return this.json('/dashboard/throughput')
  }

  getActivity(): Promise<ActivityEvent[]> {
    return this.json('/activity', { query: { limit: '50' } })
  }

  // Sandbox ------------------------------------------------------------------------------------

  getSandbox(): Promise<Sandbox> {
    return this.json('/sandbox')
  }

  startSandbox(): Promise<Sandbox> {
    return this.json('/sandbox', { method: 'POST' })
  }

  async stopSandbox(): Promise<void> {
    await this.request('/sandbox', { method: 'DELETE' })
  }

  poll(request: PollRequest): Promise<PolledContent> {
    return this.json('/sandbox/poll', { method: 'POST', body: request })
  }

  // Instances ----------------------------------------------------------------------------------

  listInstances(type: InstanceType): Promise<Instance[]> {
    return this.json(instancesPath(type))
  }

  getInstance(type: InstanceType, id: string): Promise<Instance> {
    return this.json(`${instancesPath(type)}/${id}`)
  }

  registerInstance(type: InstanceType, request: RegisterInstanceRequest): Promise<Instance> {
    return this.json(instancesPath(type), { method: 'POST', body: request })
  }

  startLocalInstance(type: InstanceType, request: StartLocalInstanceRequest): Promise<Instance> {
    return this.json(`${instancesPath(type)}/local`, { method: 'POST', body: request })
  }

  async unregisterInstance(type: InstanceType, id: string): Promise<void> {
    await this.request(`${instancesPath(type)}/${id}`, { method: 'DELETE' })
  }

  checkHealth(type: InstanceType, id: string): Promise<InstanceHealth> {
    return this.json(`${instancesPath(type)}/${id}/health`)
  }

  // LDES servers -------------------------------------------------------------------------------

  listEventStreams(serverId: string): Promise<EventStream[]> {
    return this.json(`${server(serverId)}/eventstreams`)
  }

  getEventStream(serverId: string, collection: string): Promise<EventStream> {
    return this.json(`${server(serverId)}/eventstreams/${encode(collection)}`)
  }

  createEventStream(serverId: string, definition: string, format: RdfFormat = TURTLE):
    Promise<EventStream> {
    return this.json(`${server(serverId)}/eventstreams`,
      { method: 'POST', body: definition, contentType: format })
  }

  async deleteEventStream(serverId: string, collection: string): Promise<void> {
    await this.request(`${server(serverId)}/eventstreams/${encode(collection)}`,
      { method: 'DELETE' })
  }

  listViews(serverId: string, collection: string): Promise<View[]> {
    return this.json(`${stream(serverId, collection)}/views`)
  }

  createView(serverId: string, collection: string, definition: string): Promise<View> {
    return this.json(`${stream(serverId, collection)}/views`,
      { method: 'POST', body: definition, contentType: TURTLE })
  }

  async deleteView(serverId: string, collection: string, view: string): Promise<void> {
    await this.request(`${stream(serverId, collection)}/views/${encode(view)}`,
      { method: 'DELETE' })
  }

  getShape(serverId: string, collection: string): Promise<string> {
    return this.text(`${stream(serverId, collection)}/shape`, { accept: TURTLE })
  }

  updateShape(serverId: string, collection: string, shape: string): Promise<string> {
    return this.text(`${stream(serverId, collection)}/shape`,
      { method: 'PUT', body: shape, contentType: TURTLE, accept: TURTLE })
  }

  getCatalog(serverId: string): Promise<string> {
    return this.text(`${server(serverId)}/dcat`, { accept: TURTLE })
  }

  getServerCatalog(serverId: string): Promise<ServerCatalog> {
    return this.json(`${server(serverId)}/server-catalog`)
  }

  saveServerCatalog(serverId: string, document: string): Promise<ServerCatalog> {
    return this.json(`${server(serverId)}/server-catalog`,
      { method: 'PUT', body: document, contentType: TURTLE })
  }

  async updateDcat(serverId: string, target: Exclude<DcatTarget, { kind: 'server' }>,
    dcat: string): Promise<void> {
    const path = target.kind === 'eventStream'
      ? `${stream(serverId, target.collection)}/dcat`
      : `${stream(serverId, target.collection)}/views/${encode(target.view)}/dcat`
    await this.request(path, { method: 'PUT', body: dcat, contentType: TURTLE })
  }

  async ingestMember(serverId: string, collection: string, member: string, format: RdfFormat):
    Promise<void> {
    await this.request(`${stream(serverId, collection)}/members`,
      { method: 'POST', body: member, contentType: format })
  }

  getStreamStatistics(serverId: string): Promise<StreamStatistics[]> {
    return this.json(`${server(serverId)}/statistics`)
  }

  getBackgroundProcesses(serverId: string): Promise<BackgroundProcess[]> {
    return this.json(`${server(serverId)}/processes`)
  }

  getFragment(serverId: string, collection: string, view: string, url?: string):
    Promise<Fragment> {
    return this.json(`${stream(serverId, collection)}/views/${encode(view)}/fragment`,
      { query: { url } })
  }

  // LDIOs --------------------------------------------------------------------------------------

  listPipelines(ldioId: string): Promise<DeployedPipeline[]> {
    return this.json(`${ldio(ldioId)}/pipelines`)
  }

  createPipeline(ldioId: string, pipeline: Pipeline): Promise<DeployedPipeline> {
    return this.json(`${ldio(ldioId)}/pipelines`, { method: 'POST', body: pipeline })
  }

  updatePipeline(ldioId: string, pipeline: Pipeline): Promise<DeployedPipeline> {
    return this.json(`${ldio(ldioId)}/pipelines/${encode(pipeline.name)}`,
      { method: 'PUT', body: pipeline })
  }

  async deletePipeline(ldioId: string, name: string): Promise<void> {
    await this.request(`${ldio(ldioId)}/pipelines/${encode(name)}`, { method: 'DELETE' })
  }

  async haltPipeline(ldioId: string, name: string): Promise<PipelineStatus> {
    return (await this.json<{ status: PipelineStatus }>(
      `${ldio(ldioId)}/pipelines/${encode(name)}/halt`, { method: 'POST' })).status
  }

  async resumePipeline(ldioId: string, name: string): Promise<PipelineStatus> {
    return (await this.json<{ status: PipelineStatus }>(
      `${ldio(ldioId)}/pipelines/${encode(name)}/resume`, { method: 'POST' })).status
  }

  getComponentCatalog(ldioId: string): Promise<ComponentCatalog> {
    return this.json(`${ldio(ldioId)}/catalog`)
  }

  getPipelineStatistics(ldioId: string): Promise<PipelineStatistics[]> {
    return this.json(`${ldio(ldioId)}/statistics`)
  }

  getLdesClientStatuses(ldioId: string): Promise<Record<string, LdesClientStatus>> {
    return this.json(`${ldio(ldioId)}/ldes-clients`)
  }

  async sendToPipeline(ldioId: string, pipeline: string, content: string, contentType: string):
    Promise<void> {
    await this.request(`${ldio(ldioId)}/pipelines/${encode(pipeline)}/members`,
      { method: 'POST', body: content, contentType })
  }

  // Helpers ------------------------------------------------------------------------------------

  private async json<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const response = await this.request(path, { accept: 'application/json', ...options })
    return response.json() as Promise<T>
  }

  private async text(path: string, options: RequestOptions = {}): Promise<string> {
    const response = await this.request(path, options)
    return response.text()
  }

  private async request(path: string, options: RequestOptions): Promise<Response> {
    const query = Object.entries(options.query ?? {})
      .filter((entry): entry is [string, string] => entry[1] !== undefined && entry[1] !== '')
    const url = this.baseUrl + path + (query.length ? `?${new URLSearchParams(query)}` : '')
    const headers: Record<string, string> = {}
    if (options.accept) headers.Accept = options.accept
    let body: BodyInit | undefined
    if (options.body !== undefined) {
      headers['Content-Type'] = options.contentType ?? 'application/json'
      body = options.contentType ? String(options.body) : JSON.stringify(options.body)
    }
    let response: Response
    try {
      response = await fetch(url, { method: options.method ?? 'GET', headers, body })
    } catch {
      throw new ApiError(0, 'LDES-admin is unreachable. Check that the backend runs.')
    }
    if (!response.ok) throw await toApiError(response)
    return response
  }
}

/** The RFC 9457 problem details of the backend, or what the response tells otherwise */
async function toApiError(response: Response): Promise<ApiError> {
  const text = await response.text().catch(() => '')
  let detail: string | undefined
  try {
    const problem = JSON.parse(text) as { detail?: string; title?: string }
    detail = problem.detail ?? problem.title
  } catch {
    detail = text.trim() || undefined
  }
  return new ApiError(response.status,
    detail ?? `LDES-admin answered ${response.status} ${response.statusText}`.trim())
}

function instancesPath(type: InstanceType): string {
  return type === 'LDES_SERVER' ? '/ldes-servers' : '/ldios'
}

const encode = encodeURIComponent
const server = (id: string) => `/ldes-servers/${id}`
const stream = (id: string, collection: string) => `${server(id)}/eventstreams/${encode(collection)}`
const ldio = (id: string) => `/ldios/${id}`
