import { Parser } from 'n3'
import type {
  HealthStatus,
  Instance,
  InstanceHealth,
  InstanceType,
  RegisterInstanceRequest,
  StartLocalInstanceRequest,
} from '@/domain/instance'
import { instanceTypeLabel } from '@/domain/instance'
import type {
  BackgroundProcess,
  DcatTarget,
  EventStream,
  Fragment,
  RdfFormat,
  Relation,
  ServerCatalog,
  StreamStatistics,
  TypedConfig,
  View,
} from '@/domain/ldes'
import { backlogOf } from '@/domain/ldes'
import type {
  ComponentCatalog,
  DeployedPipeline,
  LdesClientStatus,
  Pipeline,
  PipelineStatistics,
  PipelineStatus,
} from '@/domain/ldio'
import { canBeHalted, canBeResumed } from '@/domain/ldio'
import type { ActivityEvent, DashboardOverview, ThroughputSeries } from '@/domain/dashboard'
import {
  type Sandbox,
  SANDBOX_EVENT_STREAM,
  SANDBOX_INGEST_PIPELINE,
} from '@/domain/sandbox'
import { componentsOfKind } from '@/pipeline/catalog'
import { PIPELINE_NAME } from '@/pipeline/graph'
import { PREFIXES, prefixLines as prefixLinesFor } from '@/domain/rdf'
import { localName } from '@/ldes/definition'
import { streamOf } from '@/ldes/eventStream'
import { objectOf, parseTurtle, predicatesOf, RDF_TYPE, type RdfGraph, type Resource } from '@/ldes/turtle'
import { descriptionOf, fragmentationsOf, viewOf, viewRetentionPolicies } from '@/ldes/view'
import { ApiError, type LdesAdminApi } from '../LdesAdminApi'
import {
  combinedCatalog,
  createSampleState,
  eventStreamTurtle,
  sampleMember,
  type SampleState,
  sandboxIngestPipeline,
  serverCatalogTurtle,
  toView,
} from './sampleData'

const SANDBOX_LDES_SERVER = 'Sandbox LDES server'
const SANDBOX_LDIO = 'Sandbox LDIO'

export interface MockOptions {
  /** Average response time in ms, to make loading states visible */
  latency?: number
  now?: () => Date
  /** Delay before a new pipeline starts running, and before a local instance is up */
  startupDelay?: number
}

/**
 * The LDES-admin API on in-memory sample data. Behaves like the backend, including its error
 * responses, so the frontend can be developed and demonstrated without servers.
 */
export class MockLdesAdminApi implements LdesAdminApi {
  private state: SampleState
  private localPorts = 32772

  constructor(private readonly options: MockOptions = {}) {
    this.state = createSampleState(this.now())
  }

  // Dashboard ----------------------------------------------------------------------------------

  getDashboard(): Promise<DashboardOverview> {
    return this.respond(() => ({
      ldesServers: this.instancesOf('LDES_SERVER').map((server) => {
        const up = this.isUp(server)
        const streams = up ? this.state.statistics[server.id] ?? [] : []
        return {
          server,
          health: this.health(server),
          ingestedMembers: streams.reduce((sum, stream) => sum + stream.ingestedMembers, 0),
          streams,
          problems: up ? this.state.problems[server.id] ?? [] : [],
        }
      }),
      ldios: this.instancesOf('LDIO').map((ldio) => {
        const up = this.isUp(ldio)
        const pipelines = up ? this.state.pipelines[ldio.id] ?? [] : []
        const statistics = up ? this.state.pipelineStatistics[ldio.id] ?? [] : []
        const pipelinesByStatus: Partial<Record<PipelineStatus, number>> = {}
        pipelines.forEach(({ status }) => {
          pipelinesByStatus[status] = (pipelinesByStatus[status] ?? 0) + 1
        })
        return {
          ldio,
          health: this.health(ldio),
          pipelinesByStatus,
          processedMembers: statistics.reduce((sum, stats) => sum + stats.membersOut, 0),
          pipelines: pipelines.map(({ pipeline, status }) => {
            const stats = statistics.find((s) => s.pipeline === pipeline.name)
            return {
              name: pipeline.name,
              status,
              membersIn: stats?.membersIn ?? 0,
              membersOut: stats?.membersOut ?? 0,
            }
          }),
          problems: up ? this.state.problems[ldio.id] ?? [] : [],
        }
      }),
    }))
  }

  getThroughput(): Promise<ThroughputSeries[]> {
    return this.respond(() => this.state.throughput.filter((series) =>
      this.state.instances.some((instance) => instance.id === series.instanceId)))
  }

  getActivity(): Promise<ActivityEvent[]> {
    return this.respond(() => this.state.activity)
  }

  // Sandbox ------------------------------------------------------------------------------------

  getSandbox(): Promise<Sandbox> {
    return this.respond(() => this.sandbox())
  }

  startSandbox(): Promise<Sandbox> {
    return this.respond(() => {
      let { ldesServer, ldio } = this.sandbox()
      ldesServer ??= this.startLocal('LDES_SERVER', SANDBOX_LDES_SERVER, true)
      ldio ??= this.startLocal('LDIO', SANDBOX_LDIO, true)
      if (!this.state.eventStreams[ldesServer.id].some((s) => s.collection === SANDBOX_EVENT_STREAM)) {
        this.seedEventStream(ldesServer)
      }
      const eventStreamUrl = `${ldesServer.internalUrl}/${SANDBOX_EVENT_STREAM}`
      const pipelines = this.state.pipelines[ldio.id]
      const ingest = pipelines.find((deployed) => deployed.pipeline.name === SANDBOX_INGEST_PIPELINE)
      if (!ingest || ingest.pipeline.outputs?.[0]?.config?.endpoint !== eventStreamUrl) {
        this.state.pipelines[ldio.id] = [...pipelines.filter((deployed) => deployed !== ingest),
          sandboxIngestPipeline(eventStreamUrl)]
        this.state.pipelineStatistics[ldio.id] = [...this.state.pipelineStatistics[ldio.id]
          .filter((stats) => stats.pipeline !== SANDBOX_INGEST_PIPELINE),
        { pipeline: SANDBOX_INGEST_PIPELINE, membersIn: 0, membersOut: 0, inFlight: 0 }]
        this.log('success', ldio, `Pipeline ${SANDBOX_INGEST_PIPELINE} deployed`)
      }
      return this.sandbox()
    }, this.options.startupDelay ?? 2200)
  }

  stopSandbox(): Promise<void> {
    return this.respond(() => {
      const { ldesServer, ldio } = this.sandbox()
      for (const instance of [ldesServer, ldio]) {
        if (instance) this.remove(instance)
      }
    })
  }

  // Instances ----------------------------------------------------------------------------------

  listInstances(type: InstanceType): Promise<Instance[]> {
    return this.respond(() => this.instancesOf(type))
  }

  getInstance(type: InstanceType, id: string): Promise<Instance> {
    return this.respond(() => this.instance(type, id))
  }

  registerInstance(type: InstanceType, request: RegisterInstanceRequest): Promise<Instance> {
    return this.respond(() => {
      if (!request.name?.trim()) throw new ApiError(400, "'name' must not be blank")
      const url = normalizeUrl(request.url)
      if (this.state.instances.some((i) => i.type === type && i.url === url)) {
        throw new ApiError(409, `${type === 'LDIO' ? 'An LDIO' : 'An LDES server'} with URL ${url} is already registered`)
      }
      const instance: Instance = {
        id: crypto.randomUUID(),
        type,
        name: request.name.trim(),
        url,
        origin: 'REGISTERED',
        sandbox: request.sandbox ?? false,
        registeredAt: this.now().toISOString(),
      }
      this.add(instance, /down|unreachable/.test(url) ? 'DOWN' : 'UP')
      this.log('success', instance, `${instanceTypeLabel[type]} added`)
      return instance
    })
  }

  startLocalInstance(type: InstanceType, request: StartLocalInstanceRequest): Promise<Instance> {
    return this.respond(() => {
      if (!request.name?.trim()) throw new ApiError(400, "'name' must not be blank")
      return this.startLocal(type, request.name.trim(), request.sandbox ?? false)
    }, this.options.startupDelay ?? 2200)
  }

  unregisterInstance(type: InstanceType, id: string): Promise<void> {
    return this.respond(() => this.remove(this.instance(type, id)))
  }

  checkHealth(type: InstanceType, id: string): Promise<InstanceHealth> {
    return this.respond(() => this.health(this.instance(type, id)))
  }

  // LDES servers -------------------------------------------------------------------------------

  listEventStreams(serverId: string): Promise<EventStream[]> {
    return this.respond(() => {
      this.reachable('LDES_SERVER', serverId, 'list event streams')
      return this.state.eventStreams[serverId] ?? []
    })
  }

  getEventStream(serverId: string, collection: string): Promise<EventStream> {
    return this.respond(() => this.stream(serverId, collection, 'get event stream ' + collection))
  }

  createEventStream(serverId: string, definition: string): Promise<EventStream> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, 'create event stream')
      const reject = (reason: string) =>
        new ApiError(400, `LDES server '${server.name}' could not create event stream: 400 BAD_REQUEST - ${reason}`)
      const graph = parseDefinition(definition, reject)
      const subject = streamOf(graph)
      if (!subject) throw reject('No ldes:EventStream found in the definition')
      const collection = localName(subject.value)
      const streams = this.state.eventStreams[serverId] ?? []
      if (streams.some((stream) => stream.collection === collection)) {
        throw reject(`Collection ${collection} already exists`)
      }
      const value = (property: string) => objectOf(graph, subject, PREFIXES.ldes + property)?.value
      const stream: EventStream = {
        collection,
        timestampPath: value('timestampPath'),
        versionOfPath: value('versionOfPath'),
        createVersions: value('createVersions') === 'true',
        views: [],
        definition,
      }
      this.state.eventStreams[serverId] = [...streams, stream]
        .sort((a, b) => a.collection.localeCompare(b.collection))
      this.state.statistics[serverId] = [...(this.state.statistics[serverId] ?? []),
        { collection, ingestedMembers: 0, backlog: 0, views: [] }]
      this.state.views[`${serverId}/${collection}`] = []
      this.state.shapes[`${serverId}/${collection}`] = definition
      this.log('success', server, `Event stream ${collection} created`)
      return stream
    })
  }

  deleteEventStream(serverId: string, collection: string): Promise<void> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, 'delete event stream ' + collection)
      this.stream(serverId, collection, 'delete event stream ' + collection)
      this.state.eventStreams[serverId] = this.state.eventStreams[serverId]
        .filter((stream) => stream.collection !== collection)
      this.state.statistics[serverId] = (this.state.statistics[serverId] ?? [])
        .filter((stream) => stream.collection !== collection)
      this.log('warn', server, `Event stream ${collection} deleted`)
    })
  }

  listViews(serverId: string, collection: string): Promise<View[]> {
    return this.respond(() => {
      this.stream(serverId, collection, `list views of ${collection}`)
      return this.state.views[`${serverId}/${collection}`] ?? []
    })
  }

  createView(serverId: string, collection: string, definition: string): Promise<View> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, `create view on ${collection}`)
      const stream = this.stream(serverId, collection, `create view on ${collection}`)
      const reject = (reason: string) =>
        new ApiError(400, `LDES server '${server.name}' could not create view on ${collection}: 400 BAD_REQUEST - ${reason}`)
      const graph = parseDefinition(definition, reject)
      const subject = viewOf(graph)
      const description = descriptionOf(graph)
      if (!subject || !description) throw reject('No tree:viewDescription found in the definition')
      const name = localName(subject.value)
      const key = `${serverId}/${collection}`
      if ((this.state.views[key] ?? []).some((view) => view.name === name)) {
        throw reject(`View ${name} already exists`)
      }
      const view: View = {
        collection,
        name,
        pageSize: Number(objectOf(graph, description, `${PREFIXES.tree}pageSize`)?.value ?? 100),
        fragmentations: (fragmentationsOf(graph) ?? []).map((node) => typedConfig(graph, node)),
        retentionPolicies: viewRetentionPolicies(graph).map((node) => typedConfig(graph, node)),
        definition,
      }
      this.state.views[key] = [...(this.state.views[key] ?? []), view]
        .sort((a, b) => a.name.localeCompare(b.name))
      stream.views = [...stream.views, name].sort()
      const statistics = this.statisticsOf(serverId, collection)
      if (statistics) {
        statistics.views.push({ view: name, bucketisedMembers: 0, paginatedMembers: 0 })
        statistics.backlog = backlogOf(statistics.views)
      }
      this.log('success', server, `View ${collection}/${name} created`)
      return view
    })
  }

  deleteView(serverId: string, collection: string, view: string): Promise<void> {
    return this.respond(() => {
      const stream = this.stream(serverId, collection, `delete view ${view}`)
      const key = `${serverId}/${collection}`
      this.state.views[key] = (this.state.views[key] ?? []).filter((v) => v.name !== view)
      stream.views = stream.views.filter((name) => name !== view)
      const statistics = this.statisticsOf(serverId, collection)
      if (statistics) {
        statistics.views = statistics.views.filter((v) => v.view !== view)
        statistics.backlog = backlogOf(statistics.views)
      }
    })
  }

  getShape(serverId: string, collection: string): Promise<string> {
    return this.respond(() => {
      this.stream(serverId, collection, `get shape of ${collection}`)
      return this.state.shapes[`${serverId}/${collection}`] ?? ''
    })
  }

  updateShape(serverId: string, collection: string, shape: string): Promise<string> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, `update shape of ${collection}`)
      this.stream(serverId, collection, `update shape of ${collection}`)
      validateRdf(shape, 'text/turtle', (reason) => new ApiError(400,
        `LDES server '${server.name}' could not update shape of ${collection}: 400 BAD_REQUEST - ${reason}`))
      this.state.shapes[`${serverId}/${collection}`] = shape
      return shape
    })
  }

  getCatalog(serverId: string): Promise<string> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, 'get DCAT catalog')
      const datasets = this.state.datasets[serverId] ?? []
      return combinedCatalog(server.internalUrl ?? server.url, this.state.serverCatalogs[serverId],
        (this.state.eventStreams[serverId] ?? []).filter((s) => datasets.includes(s.collection)))
    })
  }

  getServerCatalog(serverId: string): Promise<ServerCatalog> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, 'get DCAT catalog')
      return this.state.serverCatalogs[serverId]
        ?? { catalogId: null, document: serverCatalogTurtle(server.name) }
    })
  }

  saveServerCatalog(serverId: string, document: string): Promise<ServerCatalog> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, 'create DCAT')
      validateRdf(document, 'text/turtle', (reason) =>
        new ApiError(400, `LDES server '${server.name}' could not create DCAT: 400 BAD_REQUEST - ${reason}`))
      if (!/\[\s*\]\s+(a|rdf:type)\s+dcat:Catalog/.test(document)) {
        throw new ApiError(400, `LDES server '${server.name}' could not create DCAT: 400 BAD_REQUEST - The catalog must be a blank node of type dcat:Catalog`)
      }
      const catalog = {
        catalogId: this.state.serverCatalogs[serverId]?.catalogId ?? crypto.randomUUID(),
        document,
      }
      this.state.serverCatalogs[serverId] = catalog
      return catalog
    })
  }

  updateDcat(serverId: string, target: Exclude<DcatTarget, { kind: 'server' }>, dcat: string):
    Promise<void> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, 'update DCAT')
      this.stream(serverId, target.collection, 'update DCAT')
      validateRdf(dcat, 'text/turtle', (reason) =>
        new ApiError(400, `LDES server '${server.name}' could not update DCAT: 400 BAD_REQUEST - ${reason}`))
      const datasets = this.state.datasets[serverId] ?? []
      if (!datasets.includes(target.collection)) {
        this.state.datasets[serverId] = [...datasets, target.collection]
      }
    })
  }

  ingestMember(serverId: string, collection: string, member: string, format: RdfFormat):
    Promise<void> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, `ingest member in ${collection}`)
      this.stream(serverId, collection, `ingest member in ${collection}`)
      validateRdf(member, format, (reason) => new ApiError(400,
        `LDES server '${server.name}' could not ingest member in ${collection}: 400 BAD_REQUEST - ${reason}`))
      this.countIngested(serverId, collection)
    })
  }

  getStreamStatistics(serverId: string): Promise<StreamStatistics[]> {
    return this.respond(() => {
      this.reachable('LDES_SERVER', serverId, 'get metrics')
      return this.state.statistics[serverId] ?? []
    })
  }

  getBackgroundProcesses(serverId: string): Promise<BackgroundProcess[]> {
    return this.respond(() => {
      this.reachable('LDES_SERVER', serverId, 'get background processes')
      return this.state.processes[serverId] ?? []
    })
  }

  getFragment(serverId: string, collection: string, view: string, url?: string):
    Promise<Fragment> {
    return this.respond(() => {
      const server = this.reachable('LDES_SERVER', serverId, `fetch ${collection}/${view}`)
      const definition = (this.state.views[`${serverId}/${collection}`] ?? [])
        .find((v) => v.name === view)
      if (!definition) {
        throw new ApiError(404, `LDES server '${server.name}' could not fetch ${collection}/${view}: 404 NOT_FOUND`)
      }
      // Like the real server, a local one publishes its IRIs on its internal URL
      return buildFragment(`${server.internalUrl ?? server.url}/${collection}/${view}`, url,
        collection, definition, this.now())
    })
  }

  // LDIOs --------------------------------------------------------------------------------------

  listPipelines(ldioId: string): Promise<DeployedPipeline[]> {
    return this.respond(() => {
      this.reachable('LDIO', ldioId, 'list pipelines')
      return this.state.pipelines[ldioId] ?? []
    })
  }

  createPipeline(ldioId: string, pipeline: Pipeline): Promise<DeployedPipeline> {
    return this.respond(() => {
      const ldio = this.reachable('LDIO', ldioId, `create pipeline ${pipeline.name}`)
      if (!PIPELINE_NAME.test(pipeline.name)) {
        throw new ApiError(400, `'name' must consist of letters, digits, '-' and '_', but was ${pipeline.name}`)
      }
      const pipelines = this.state.pipelines[ldioId] ?? []
      if (pipelines.some((deployed) => deployed.pipeline.name === pipeline.name)) {
        throw new ApiError(400, `LDIO '${ldio.name}' could not create pipeline ${pipeline.name}: 400 BAD_REQUEST - Pipeline ${pipeline.name} already exists`)
      }
      const deployed: DeployedPipeline = { pipeline, status: 'INIT', updateSource: 'AUTO' }
      this.state.pipelines[ldioId] = [...pipelines, deployed]
      this.state.pipelineStatistics[ldioId] = [...(this.state.pipelineStatistics[ldioId] ?? []),
        { pipeline: pipeline.name, membersIn: 0, membersOut: 0, inFlight: 0 }]
      this.startSoon(ldioId, pipeline.name)
      this.log('success', ldio, `Pipeline ${pipeline.name} deployed`)
      return deployed
    })
  }

  updatePipeline(ldioId: string, pipeline: Pipeline): Promise<DeployedPipeline> {
    return this.respond(() => {
      const ldio = this.reachable('LDIO', ldioId, `update pipeline ${pipeline.name}`)
      const deployed = this.pipeline(ldioId, pipeline.name)
      deployed.pipeline = pipeline
      deployed.status = 'INIT'
      deployed.updateSource = 'AUTO'
      this.startSoon(ldioId, pipeline.name)
      this.log('info', ldio, `Pipeline ${pipeline.name} updated`)
      return deployed
    })
  }

  deletePipeline(ldioId: string, name: string): Promise<void> {
    return this.respond(() => {
      const ldio = this.reachable('LDIO', ldioId, `delete pipeline ${name}`)
      this.pipeline(ldioId, name)
      this.state.pipelines[ldioId] = this.state.pipelines[ldioId]
        .filter((deployed) => deployed.pipeline.name !== name)
      this.log('warn', ldio, `Pipeline ${name} deleted`)
    })
  }

  haltPipeline(ldioId: string, name: string): Promise<PipelineStatus> {
    return this.respond(() => {
      const ldio = this.reachable('LDIO', ldioId, `halt pipeline ${name}`)
      const deployed = this.pipeline(ldioId, name)
      if (!canBeHalted(deployed.status)) {
        throw new ApiError(409, `Cannot halt pipeline ${name}, because it is ${deployed.status}`)
      }
      deployed.status = 'HALTED'
      deployed.updateSource = 'MANUAL'
      this.log('warn', ldio, `Pipeline ${name} halted`)
      return deployed.status
    })
  }

  resumePipeline(ldioId: string, name: string): Promise<PipelineStatus> {
    return this.respond(() => {
      const ldio = this.reachable('LDIO', ldioId, `resume pipeline ${name}`)
      const deployed = this.pipeline(ldioId, name)
      if (!canBeResumed(deployed.status)) {
        throw new ApiError(409, `Cannot resume pipeline ${name}, because it is ${deployed.status}`)
      }
      deployed.status = 'RUNNING'
      deployed.updateSource = 'MANUAL'
      this.log('success', ldio, `Pipeline ${name} resumed`)
      return deployed.status
    })
  }

  getComponentCatalog(ldioId: string): Promise<ComponentCatalog> {
    return this.respond(() => {
      this.reachable('LDIO', ldioId, 'get component catalog')
      const ids = (kind: Parameters<typeof componentsOfKind>[0]) =>
        componentsOfKind(kind).map((component) => component.id).sort()
      return {
        inputs: ids('input'),
        adapters: ids('adapter'),
        transformers: ids('transformer'),
        outputs: ids('output'),
      }
    })
  }

  getPipelineStatistics(ldioId: string): Promise<PipelineStatistics[]> {
    return this.respond(() => {
      this.reachable('LDIO', ldioId, 'get metrics')
      return this.state.pipelineStatistics[ldioId] ?? []
    })
  }

  getLdesClientStatuses(ldioId: string): Promise<Record<string, LdesClientStatus>> {
    return this.respond(() => {
      this.reachable('LDIO', ldioId, 'get LDES client statuses')
      return this.state.clientStatuses[ldioId] ?? {}
    })
  }

  sendToPipeline(ldioId: string, pipeline: string, content: string, contentType: string):
    Promise<void> {
    return this.respond(() => {
      const ldio = this.reachable('LDIO', ldioId, `receive a message in pipeline ${pipeline}`)
      if (!content.trim()) throw new ApiError(400, 'A message must not be empty')
      const deployed = this.pipeline(ldioId, pipeline)
      const input = deployed.pipeline.input.name
      if (input !== 'Ldio:HttpIn') {
        throw new ApiError(400, `Pipeline ${pipeline} reads from ${input}, so it does not accept members over HTTP`)
      }
      const rdf = RDF_FORMATS.find((format) => contentType.startsWith(format))
      if (rdf) {
        validateRdf(content, rdf, (reason) => new ApiError(400,
          `LDIO '${ldio.name}' could not receive a message in pipeline ${pipeline}: 400 BAD_REQUEST - ${reason}`))
      }
      const stats = this.state.pipelineStatistics[ldioId]?.find((s) => s.pipeline === pipeline)
      if (stats) {
        stats.membersIn += 1
        stats.membersOut += 1
      }
      // An HTTP output that points to the event stream of a local LDES server ingests there
      for (const output of deployed.pipeline.outputs ?? []) {
        const endpoint = output.name === 'Ldio:HttpOut' ? String(output.config?.endpoint ?? '') : ''
        const server = this.instancesOf('LDES_SERVER').find((candidate) => candidate.internalUrl
          && endpoint.startsWith(`${candidate.internalUrl}/`))
        if (server && this.isUp(server)) {
          this.countIngested(server.id, endpoint.slice(server.internalUrl!.length + 1))
        }
      }
    })
  }

  // Helpers ------------------------------------------------------------------------------------

  private sandbox(): Sandbox {
    const find = (type: InstanceType, name: string) => this.instancesOf(type)
      .find((instance) => instance.origin === 'LOCAL' && instance.sandbox && instance.name === name)
      ?? null
    const ldesServer = find('LDES_SERVER', SANDBOX_LDES_SERVER)
    const ldio = find('LDIO', SANDBOX_LDIO)
    return {
      state: ldesServer && ldio ? 'RUNNING' : ldesServer || ldio ? 'PARTIAL' : 'STOPPED',
      ldesServer,
      ldio,
      eventStream: SANDBOX_EVENT_STREAM,
      ingestPipeline: SANDBOX_INGEST_PIPELINE,
      eventStreamUrl: ldesServer
        ? `${ldesServer.internalUrl ?? ldesServer.url}/${SANDBOX_EVENT_STREAM}` : null,
    }
  }

  private startLocal(type: InstanceType, name: string, sandbox: boolean): Instance {
    const alias = slug(name) || slug(instanceTypeLabel[type])
    const taken = (candidate: string) => this.state.instances
      .some((instance) => instance.internalUrl === `http://${candidate}:8080`)
    let host = alias
    for (let i = 2; taken(host); i++) host = `${alias}-${i}`
    const instance: Instance = {
      id: crypto.randomUUID(),
      type,
      name,
      url: `http://localhost:${this.localPorts++}`,
      internalUrl: `http://${host}:8080`,
      origin: 'LOCAL',
      sandbox,
      registeredAt: this.now().toISOString(),
    }
    this.add(instance, 'UP')
    this.log('success', instance, `Local ${instanceTypeLabel[type]} started`,
      `On ${instance.url}, and on ${instance.internalUrl} for other local instances`)
    return instance
  }

  private seedEventStream(server: Instance): void {
    const base = server.internalUrl ?? server.url
    const spec = {
      collection: SANDBOX_EVENT_STREAM,
      title: 'Sandbox parcels',
      description: 'Try out ingesting and fragmenting parcels',
      targetClass: 'perceel:Perceel',
      timestampPath: 'prov:generatedAtTime',
      properties: [],
      ingested: 0,
      views: [
        { name: 'by-page', pageSize: 10, bucketised: 0, paginated: 0 },
        {
          name: 'by-time',
          pageSize: 10,
          fragmentations: [{
            type: 'HierarchicalTimeBasedFragmentation',
            properties: { maxGranularity: 'day', fragmentationPath: `${PREFIXES.prov}generatedAtTime` },
          }],
          bucketised: 0,
          paginated: 0,
        },
      ],
    }
    this.state.eventStreams[server.id] = [...this.state.eventStreams[server.id], {
      collection: SANDBOX_EVENT_STREAM,
      timestampPath: `${PREFIXES.prov}generatedAtTime`,
      versionOfPath: `${PREFIXES.dcterms}isVersionOf`,
      createVersions: false,
      views: spec.views.map((view) => view.name),
      definition: eventStreamTurtle(base, spec),
    }]
    this.state.views[`${server.id}/${SANDBOX_EVENT_STREAM}`] = spec.views
      .map((view) => toView(base, SANDBOX_EVENT_STREAM, view))
    this.state.statistics[server.id] = [...this.state.statistics[server.id], {
      collection: SANDBOX_EVENT_STREAM,
      ingestedMembers: 0,
      backlog: 0,
      views: spec.views.map((view) => ({ view: view.name, bucketisedMembers: 0, paginatedMembers: 0 })),
    }]
    this.state.shapes[`${server.id}/${SANDBOX_EVENT_STREAM}`] = `${prefixLinesFor('sh')}

[] a sh:NodeShape .
`
    this.log('success', server, `Event stream ${SANDBOX_EVENT_STREAM} created`)
    spec.views.forEach((view) =>
      this.log('success', server, `View ${SANDBOX_EVENT_STREAM}/${view.name} created`))
  }

  private remove(instance: Instance): void {
    this.state.instances = this.state.instances.filter((candidate) => candidate.id !== instance.id)
    this.log('info', instance, instance.origin === 'LOCAL'
      ? `Local ${instanceTypeLabel[instance.type]} stopped`
      : `${instanceTypeLabel[instance.type]} removed`)
  }

  private countIngested(serverId: string, collection: string): void {
    const statistics = this.statisticsOf(serverId, collection)
    if (!statistics) return
    statistics.ingestedMembers += 1
    statistics.views.forEach((view) => {
      view.bucketisedMembers += 1
      view.paginatedMembers += 1
    })
  }

  private now(): Date {
    return this.options.now?.() ?? new Date()
  }

  /**
   * Answers after a realistic delay, with a copy of the data, like an HTTP response would.
   */
  private async respond<T>(answer: () => T, latency = this.options.latency ?? 280): Promise<T> {
    if (latency > 0) {
      await new Promise((resolve) => setTimeout(resolve, latency * (0.6 + Math.random() * 0.8)))
    }
    const result = answer()
    return result === undefined ? result : JSON.parse(JSON.stringify(result))
  }

  private instancesOf(type: InstanceType): Instance[] {
    return this.state.instances.filter((instance) => instance.type === type)
      .sort((a, b) => a.name.localeCompare(b.name))
  }

  private instance(type: InstanceType, id: string): Instance {
    const instance = this.state.instances.find((i) => i.id === id && i.type === type)
    if (!instance) throw new ApiError(404, `No ${type} found with id ${id}`)
    return instance
  }

  private health(instance: Instance): InstanceHealth {
    return { status: this.state.health[instance.id] ?? 'UNKNOWN', checkedAt: this.now().toISOString() }
  }

  private isUp(instance: Instance): boolean {
    return this.state.health[instance.id] === 'UP'
  }

  /** An instance that is up; an unreachable one fails like the backend does (502) */
  private reachable(type: InstanceType, id: string, action: string): Instance {
    const instance = this.instance(type, id)
    if (!this.isUp(instance)) {
      throw new ApiError(502, `${instanceTypeLabel[type]} '${instance.name}' could not ${action}: ${instance.url} is unreachable`)
    }
    return instance
  }

  private stream(serverId: string, collection: string, action: string): EventStream {
    const server = this.reachable('LDES_SERVER', serverId, action)
    const stream = (this.state.eventStreams[serverId] ?? [])
      .find((s) => s.collection === collection)
    if (!stream) {
      throw new ApiError(404, `LDES server '${server.name}' could not ${action}: 404 NOT_FOUND`)
    }
    return stream
  }

  private statisticsOf(serverId: string, collection: string): StreamStatistics | undefined {
    return (this.state.statistics[serverId] ?? []).find((s) => s.collection === collection)
  }

  private pipeline(ldioId: string, name: string): DeployedPipeline {
    const deployed = (this.state.pipelines[ldioId] ?? []).find((p) => p.pipeline.name === name)
    if (!deployed) throw new ApiError(404, `No pipeline found with name ${name}`)
    return deployed
  }

  private add(instance: Instance, health: HealthStatus): void {
    this.state.instances.push(instance)
    this.state.health[instance.id] = health
    if (instance.type === 'LDES_SERVER') {
      this.state.eventStreams[instance.id] = []
      this.state.statistics[instance.id] = []
      this.state.serverCatalogs[instance.id] = { catalogId: null, document: serverCatalogTurtle(instance.name) }
      this.state.datasets[instance.id] = []
      this.state.processes[instance.id] = []
    } else {
      this.state.pipelines[instance.id] = []
      this.state.pipelineStatistics[instance.id] = []
    }
  }

  private startSoon(ldioId: string, name: string): void {
    setTimeout(() => {
      const deployed = this.state.pipelines[ldioId]?.find((p) => p.pipeline.name === name)
      if (deployed?.status === 'INIT') deployed.status = 'RUNNING'
    }, this.options.startupDelay ?? 2200)
  }

  private log(severity: ActivityEvent['severity'], instance: Instance, title: string,
    detail?: string): void {
    this.state.activity = [{
      id: crypto.randomUUID(),
      time: this.now().toISOString(),
      severity,
      source: { type: instance.type, id: instance.id, name: instance.name },
      title,
      detail,
    }, ...this.state.activity].slice(0, 50)
  }
}

// Fragments ----------------------------------------------------------------------------------

const MEMBERS_PER_PAGE = 5

/**
 * Builds a page of a view the way the LDES server fetch API serves it: the view root links to
 * buckets (per fragmentation) or straight to the first page; pages link to the next page.
 */
function buildFragment(root: string, url: string | undefined, collection: string, view: View,
  now: Date): Fragment {
  const target = new URL(url ?? root)
  const params = target.searchParams
  const page = Number(params.get('pageNumber') ?? 0)
  const fragmentation: TypedConfig | undefined = view.fragmentations[0]
  const isRoot = !url || url === root
  const fragment: Fragment = {
    url: target.toString(),
    collection,
    view: view.name,
    root: isRoot,
    immutable: false,
    relations: [],
    members: [],
  }
  const path = fragmentation?.properties.fragmentationPath

  if (isRoot) {
    fragment.relations = rootRelations(root, fragmentation, view, now, path)
    return fragment
  }

  const pages = fragmentation ? 3 : 6
  const bucket = [...params.entries()].filter(([key]) => key !== 'pageNumber')
    .map(([, value]) => value).join('/')
  const seed = [...bucket].reduce((sum, char) => sum + char.charCodeAt(0), 0)
  const day = params.get('day')
    ? new Date(Date.UTC(Number(params.get('year')), Number(params.get('month')) - 1,
      Number(params.get('day')), 6))
    : new Date(now.getTime() - 86_400_000)
  fragment.members = Array.from({ length: MEMBERS_PER_PAGE }, (_, i) => {
    const index = seed + (page - 1) * MEMBERS_PER_PAGE + i
    const timestamp = new Date(day.getTime() + ((page - 1) * MEMBERS_PER_PAGE + i) * 1_740_000)
    const member = sampleMember(collection, index, timestamp)
    return { ...member, timestamp: timestamp.toISOString() }
  })
  fragment.immutable = page < pages
  if (page < pages) {
    const next = new URL(target)
    next.searchParams.set('pageNumber', String(page + 1))
    fragment.relations = [{
      type: 'Relation',
      node: next.toString(),
      remainingItems: (pages - page) * view.pageSize,
    }]
  }
  return fragment
}

function rootRelations(root: string, fragmentation: TypedConfig | undefined, view: View,
  now: Date, path: string | undefined): Relation[] {
  const firstPage = (params: Record<string, string>) => {
    const url = new URL(root)
    Object.entries({ ...params, pageNumber: '1' }).forEach(([k, v]) => url.searchParams.set(k, v))
    return url.toString()
  }
  switch (fragmentation?.type) {
    case 'HierarchicalTimeBasedFragmentation':
      return [2, 1, 0].map((daysAgo) => {
        const day = new Date(now.getTime() - daysAgo * 86_400_000)
        day.setUTCHours(0, 0, 0, 0)
        return {
          type: 'GreaterThanOrEqualToRelation',
          path,
          value: day.toISOString(),
          node: firstPage({
            year: String(day.getUTCFullYear()),
            month: String(day.getUTCMonth() + 1),
            day: String(day.getUTCDate()),
          }),
          remainingItems: 3 * view.pageSize,
        }
      })
    case 'GeospatialFragmentation':
      return [[16812, 10970], [16813, 10970], [16812, 10971], [16814, 10972]].map(([x, y]) => ({
        type: 'GeospatiallyContainsRelation',
        path,
        value: `POLYGON ((${tileLon(x)} ${tileLat(y)}, ${tileLon(x + 1)} ${tileLat(y)}, ${tileLon(x + 1)} ${tileLat(y + 1)}, ${tileLon(x)} ${tileLat(y + 1)}, ${tileLon(x)} ${tileLat(y)}))`,
        node: firstPage({ tile: `15/${x}/${y}` }),
      }))
    case 'ReferenceFragmentation': {
      const key = fragmentation.properties.fragmentationKey ?? 'reference'
      return ['Antwerpen', 'Brugge', 'Gent', 'Hasselt', 'Leuven', 'Mechelen'].map((value) => ({
        type: 'EqualToRelation',
        path,
        value,
        node: firstPage({ [key]: value }),
      }))
    }
    default:
      return [{
        type: 'Relation',
        node: firstPage({}),
        remainingItems: 6 * view.pageSize,
      }]
  }
}

const tileLon = (x: number) => ((x / 2 ** 15) * 360 - 180).toFixed(4)
const tileLat = (y: number) => {
  const n = Math.PI - (2 * Math.PI * y) / 2 ** 15
  return ((180 / Math.PI) * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)))).toFixed(4)
}

// RDF helpers --------------------------------------------------------------------------------

function validateRdf(content: string, format: RdfFormat, reject: (reason: string) => Error): void {
  if (!content.trim()) throw reject('The body is empty')
  try {
    if (format === 'application/ld+json') {
      JSON.parse(content)
    } else {
      const n3Format = format === 'application/n-quads' ? 'N-Quads'
        : format === 'application/n-triples' ? 'N-Triples' : 'Turtle'
      new Parser({ format: n3Format, baseIRI: 'http://localhost/' }).parse(content)
    }
  } catch (e) {
    throw reject(`Invalid RDF: ${(e instanceof Error ? e.message : String(e)).split('\n')[0]}`)
  }
}

function parseDefinition(definition: string, reject: (reason: string) => Error): RdfGraph {
  validateRdf(definition, 'text/turtle', reject)
  const parsed = parseTurtle(definition)
  if (!parsed.ok) throw reject(`Invalid RDF: ${parsed.error}`)
  return parsed.graph
}

/** A fragmentation or retention policy as the backend describes it: local names and values */
function typedConfig(graph: RdfGraph, node: Resource): TypedConfig {
  return {
    type: localName(objectOf(graph, node, RDF_TYPE)?.value ?? ''),
    properties: Object.fromEntries(predicatesOf(graph, node)
      .filter((predicate) => predicate !== RDF_TYPE)
      .map((predicate) => [localName(predicate), objectOf(graph, node, predicate)!.value])),
  }
}

const RDF_FORMATS: RdfFormat[] = ['text/turtle', 'application/ld+json', 'application/n-quads',
  'application/n-triples']

/** A host name derived from a name, like the backend does: "Sandbox LDIO" becomes sandbox-ldio */
function slug(name: string): string {
  return name.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 50).replace(/-+$/, '')
}

function normalizeUrl(value: string): string {
  let url: URL
  try {
    url = new URL(value)
  } catch {
    throw new ApiError(400, `'url' must be an absolute http(s) URL, but was ${value}`)
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new ApiError(400, `'url' must be an absolute http(s) URL, but was ${value}`)
  }
  return url.toString().replace(/\/+$/, '')
}
