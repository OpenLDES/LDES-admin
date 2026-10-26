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

/**
 * Everything the frontend needs from the backend. Each method corresponds to an endpoint of the
 * LDES-admin REST API (see the comments). In production mode the frontend talks to the backend
 * (./http); in demo mode it runs on in-memory sample data (./mock).
 */
export interface LdesAdminApi {
  /** GET /api/v1/dashboard */
  getDashboard(): Promise<DashboardOverview>
  /** GET /api/v1/dashboard/throughput: members ingested per hour, per LDES server */
  getThroughput(): Promise<ThroughputSeries[]>
  /** GET /api/v1/activity: the latest events of all instances, newest first */
  getActivity(): Promise<ActivityEvent[]>

  /** GET /api/v1/sandbox */
  getSandbox(): Promise<Sandbox>
  /** POST /api/v1/sandbox: starts what does not run yet, and prepares it */
  startSandbox(): Promise<Sandbox>
  /** DELETE /api/v1/sandbox */
  stopSandbox(): Promise<void>
  /**
   * POST /api/v1/sandbox/poll: fetches a URL the way an HTTP In Poller does, and answers what it
   * received, for the simulation
   */
  poll(request: PollRequest): Promise<PolledContent>

  /** GET /api/v1/ldes-servers | /api/v1/ldios */
  listInstances(type: InstanceType): Promise<Instance[]>
  /** GET /api/v1/{ldes-servers|ldios}/{id} */
  getInstance(type: InstanceType, id: string): Promise<Instance>
  /** POST /api/v1/{ldes-servers|ldios} */
  registerInstance(type: InstanceType, request: RegisterInstanceRequest): Promise<Instance>
  /** POST /api/v1/{ldes-servers|ldios}/local */
  startLocalInstance(type: InstanceType, request: StartLocalInstanceRequest): Promise<Instance>
  /** DELETE /api/v1/{ldes-servers|ldios}/{id} */
  unregisterInstance(type: InstanceType, id: string): Promise<void>
  /** GET /api/v1/{ldes-servers|ldios}/{id}/health */
  checkHealth(type: InstanceType, id: string): Promise<InstanceHealth>

  /** GET /api/v1/ldes-servers/{id}/eventstreams */
  listEventStreams(serverId: string): Promise<EventStream[]>
  /** GET /api/v1/ldes-servers/{id}/eventstreams/{collection} */
  getEventStream(serverId: string, collection: string): Promise<EventStream>
  /** POST /api/v1/ldes-servers/{id}/eventstreams */
  createEventStream(serverId: string, definition: string, format?: RdfFormat): Promise<EventStream>
  /** DELETE /api/v1/ldes-servers/{id}/eventstreams/{collection} */
  deleteEventStream(serverId: string, collection: string): Promise<void>
  /** GET /api/v1/ldes-servers/{id}/eventstreams/{collection}/views */
  listViews(serverId: string, collection: string): Promise<View[]>
  /** POST /api/v1/ldes-servers/{id}/eventstreams/{collection}/views */
  createView(serverId: string, collection: string, definition: string): Promise<View>
  /** DELETE /api/v1/ldes-servers/{id}/eventstreams/{collection}/views/{view} */
  deleteView(serverId: string, collection: string, view: string): Promise<void>
  /** GET /api/v1/ldes-servers/{id}/eventstreams/{collection}/shape */
  getShape(serverId: string, collection: string): Promise<string>
  /** PUT /api/v1/ldes-servers/{id}/eventstreams/{collection}/shape */
  updateShape(serverId: string, collection: string, shape: string): Promise<string>
  /** GET /api/v1/ldes-servers/{id}/dcat: the catalog combined with datasets and data services */
  getCatalog(serverId: string): Promise<string>
  /** GET /api/v1/ldes-servers/{id}/server-catalog */
  getServerCatalog(serverId: string): Promise<ServerCatalog>
  /** PUT /api/v1/ldes-servers/{id}/server-catalog: creates or replaces it */
  saveServerCatalog(serverId: string, document: string): Promise<ServerCatalog>
  /** PUT /api/v1/ldes-servers/{id}/eventstreams/{collection}/[views/{view}/]dcat */
  updateDcat(serverId: string, target: Exclude<DcatTarget, { kind: 'server' }>, dcat: string):
    Promise<void>
  /** POST /api/v1/ldes-servers/{id}/eventstreams/{collection}/members */
  ingestMember(serverId: string, collection: string, member: string, format: RdfFormat):
    Promise<void>
  /** GET /api/v1/ldes-servers/{id}/statistics */
  getStreamStatistics(serverId: string): Promise<StreamStatistics[]>
  /** GET /api/v1/ldes-servers/{id}/processes: the steps of the fragmentation and maintenance jobs */
  getBackgroundProcesses(serverId: string): Promise<BackgroundProcess[]>
  /** GET /api/v1/ldes-servers/{id}/eventstreams/{collection}/views/{view}/fragment[?url=] */
  getFragment(serverId: string, collection: string, view: string, url?: string): Promise<Fragment>

  /** GET /api/v1/ldios/{id}/pipelines */
  listPipelines(ldioId: string): Promise<DeployedPipeline[]>
  /** POST /api/v1/ldios/{id}/pipelines */
  createPipeline(ldioId: string, pipeline: Pipeline): Promise<DeployedPipeline>
  /** PUT /api/v1/ldios/{id}/pipelines/{name} */
  updatePipeline(ldioId: string, pipeline: Pipeline): Promise<DeployedPipeline>
  /** DELETE /api/v1/ldios/{id}/pipelines/{name} */
  deletePipeline(ldioId: string, name: string): Promise<void>
  /** POST /api/v1/ldios/{id}/pipelines/{name}/halt */
  haltPipeline(ldioId: string, name: string): Promise<PipelineStatus>
  /** POST /api/v1/ldios/{id}/pipelines/{name}/resume */
  resumePipeline(ldioId: string, name: string): Promise<PipelineStatus>
  /** GET /api/v1/ldios/{id}/catalog */
  getComponentCatalog(ldioId: string): Promise<ComponentCatalog>
  /** GET /api/v1/ldios/{id}/statistics */
  getPipelineStatistics(ldioId: string): Promise<PipelineStatistics[]>
  /** GET /api/v1/ldios/{id}/ldes-clients: status of the pipelines with an LDES client input */
  getLdesClientStatuses(ldioId: string): Promise<Record<string, LdesClientStatus>>
  /**
   * POST /api/v1/ldios/{id}/pipelines/{name}/members: posts a message to a pipeline with an HTTP
   * input; its adapter decides which content types it understands
   */
  sendToPipeline(ldioId: string, pipeline: string, content: string, contentType: string):
    Promise<void>
}

/**
 * An error response of the API, modelled after the RFC 9457 problem details the backend returns.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string,
  ) {
    super(detail)
    this.name = 'ApiError'
  }
}
