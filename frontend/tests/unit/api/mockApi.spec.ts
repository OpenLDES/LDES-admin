import { ApiError } from '@/api/LdesAdminApi'
import { MockLdesAdminApi } from '@/api/mock/MockLdesAdminApi'
import { IDS } from '@/api/mock/sampleData'

describe('mock LDES-admin API', () => {
  let api: MockLdesAdminApi

  beforeEach(() => {
    api = new MockLdesAdminApi({ latency: 0, startupDelay: 0 })
  })

  it('summarises every instance on the dashboard', async () => {
    const overview = await api.getDashboard()

    expect(overview.ldesServers.map((s) => s.server.name)).toEqual(['Legacy 3.x', 'Production', 'Sandbox', 'Staging'])
    const legacy = overview.ldesServers[0]
    expect(legacy.health.status).toBe('DOWN')
    expect(legacy.streams).toEqual([])
    const ingest = overview.ldios.find((l) => l.ldio.id === IDS.ingestLdio)!
    expect(ingest.pipelinesByStatus).toEqual({ RUNNING: 2, HALTED: 1 })
  })

  it('registers an instance and refuses the same URL twice', async () => {
    const created = await api.registerInstance('LDES_SERVER', { name: 'Acceptance', url: 'https://acc.example.org/' })

    expect(created).toMatchObject({ name: 'Acceptance', url: 'https://acc.example.org', origin: 'REGISTERED' })
    await expect(api.registerInstance('LDES_SERVER', { name: 'Again', url: 'https://acc.example.org' }))
      .rejects.toEqual(new ApiError(409, 'A LDES_SERVER with URL https://acc.example.org is already registered'))
  })

  it('rejects URLs that are not absolute http(s) URLs', async () => {
    await expect(api.registerInstance('LDIO', { name: 'Bad', url: 'ftp://ldio' })).rejects.toMatchObject({ status: 400 })
  })

  it('reports an unreachable server like the backend does', async () => {
    await expect(api.listEventStreams(IDS.legacy)).rejects.toEqual(new ApiError(502,
      "LDES server 'Legacy 3.x' could not list event streams: https://legacy.ldes.example.org is unreachable"))
  })

  it('creates an event stream from its Turtle definition', async () => {
    const stream = await api.createEventStream(IDS.sandboxServer, `
      @prefix ldes: <https://w3id.org/ldes#> .
      @prefix dcterms: <http://purl.org/dc/terms/> .
      @prefix server: <http://localhost:32768/> .
      server:trees a ldes:EventStream ;
        ldes:timestampPath dcterms:created ;
        ldes:versionOfPath dcterms:isVersionOf .`)

    expect(stream).toMatchObject({
      collection: 'trees',
      timestampPath: 'http://purl.org/dc/terms/created',
      createVersions: false,
    })
    expect((await api.listEventStreams(IDS.sandboxServer)).map((s) => s.collection)).toContain('trees')
  })

  it('validates ingested members and counts them', async () => {
    await expect(api.ingestMember(IDS.sandboxServer, 'sandbox-parcels', 'not turtle', 'text/turtle'))
      .rejects.toMatchObject({ status: 400 })

    await api.ingestMember(IDS.sandboxServer, 'sandbox-parcels', '<a> <b> <c> .', 'text/turtle')

    const statistics = await api.getStreamStatistics(IDS.sandboxServer)
    expect(statistics[0].ingestedMembers).toBe(13)
  })

  it('only halts running pipelines', async () => {
    expect(await api.haltPipeline(IDS.ingestLdio, 'parcels-ingest')).toBe('HALTED')
    await expect(api.haltPipeline(IDS.ingestLdio, 'parcels-ingest')).rejects.toEqual(
      new ApiError(409, 'Cannot halt pipeline parcels-ingest, because it is HALTED'))
    expect(await api.resumePipeline(IDS.ingestLdio, 'parcels-ingest')).toBe('RUNNING')
  })

  it('starts new pipelines in INIT and runs them shortly after', async () => {
    const created = await api.createPipeline(IDS.sandboxLdio, {
      name: 'fresh',
      input: { name: 'Ldio:HttpIn', adapter: { name: 'Ldio:RdfAdapter' } },
      outputs: [{ name: 'Ldio:ConsoleOut' }],
    })
    expect(created.status).toBe('INIT')

    await new Promise((resolve) => setTimeout(resolve, 5))

    const pipelines = await api.listPipelines(IDS.sandboxLdio)
    expect(pipelines.find((p) => p.pipeline.name === 'fresh')?.status).toBe('RUNNING')
  })

  it('serves the fragments of a time-based view, from the root to the members', async () => {
    const root = await api.getFragment(IDS.production, 'parcels', 'by-time')

    expect(root.root).toBe(true)
    expect(root.relations).toHaveLength(3)
    expect(root.relations[0].type).toBe('GreaterThanOrEqualToRelation')

    const page = await api.getFragment(IDS.production, 'parcels', 'by-time', root.relations[0].node)
    expect(page.members).toHaveLength(5)
    expect(page.members[0].content).toContain('perceel:Perceel')
    expect(page.relations[0].label).toBe('Page 2')
    expect(page.immutable).toBe(true)
  })

  it('returns copies, like an HTTP response', async () => {
    const [first] = await api.listInstances('LDIO')
    first.name = 'changed'

    expect((await api.listInstances('LDIO'))[0].name).not.toBe('changed')
  })
})
