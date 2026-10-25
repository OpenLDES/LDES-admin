import { ApiError } from '@/api/LdesAdminApi'
import { MockLdesAdminApi } from '@/api/mock/MockLdesAdminApi'
import { IDS } from '@/api/mock/sampleData'
import { relationLabel } from '@/domain/ldes'

describe('mock LDES-admin API', () => {
  let api: MockLdesAdminApi

  beforeEach(() => {
    api = new MockLdesAdminApi({ latency: 0, startupDelay: 0 })
  })

  it('summarises every instance on the dashboard', async () => {
    const overview = await api.getDashboard()

    expect(overview.ldesServers.map((s) => s.server.name)).toEqual(['Legacy 3.x', 'Production', 'Sandbox LDES server', 'Staging'])
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
      .rejects.toEqual(new ApiError(409, 'An LDES server with URL https://acc.example.org is already registered'))
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

  it('creates a view with the fragmentations and retention policies of its definition', async () => {
    const view = await api.createView(IDS.sandboxServer, 'sandbox-parcels', `
      @prefix ldes: <https://w3id.org/ldes#> .
      @prefix tree: <https://w3id.org/tree#> .
      @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
      @prefix server: <http://localhost:32768/sandbox-parcels/> .
      server:by-zone tree:viewDescription [
        tree:fragmentationStrategy ( [ a tree:GeospatialFragmentation ; tree:maxZoom 14 ;
          tree:fragmentationPath <http://www.opengis.net/ont/geosparql#asWKT> ] ) ;
        ldes:retentionPolicy [ a ldes:LatestVersionSubset ; ldes:amount 2 ] ;
        tree:pageSize "50"^^xsd:int
      ] .`)

    expect(view).toMatchObject({
      name: 'by-zone',
      pageSize: 50,
      fragmentations: [{
        type: 'GeospatialFragmentation',
        properties: { maxZoom: '14', fragmentationPath: 'http://www.opengis.net/ont/geosparql#asWKT' },
      }],
      retentionPolicies: [{ type: 'LatestVersionSubset', properties: { amount: '2' } }],
    })
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
    expect(relationLabel(page.relations[0])).toMatch(/^\d{4}-\d{2}-\d{2} · page 2$/)
    expect(page.immutable).toBe(true)
  })

  it('starts a sandbox whose LDIO feeds its LDES server', async () => {
    await api.stopSandbox()
    expect((await api.getSandbox()).state).toBe('STOPPED')

    const sandbox = await api.startSandbox()

    expect(sandbox.state).toBe('RUNNING')
    expect(sandbox.ldesServer?.internalUrl).toBe('http://sandbox-ldes-server:8080')
    expect(sandbox.eventStreamUrl).toBe('http://sandbox-ldes-server:8080/sandbox-parcels')
    const serverId = sandbox.ldesServer!.id
    expect((await api.listViews(serverId, 'sandbox-parcels')).map((v) => v.name))
      .toEqual(['by-page', 'by-time'])
    const [ingest] = await api.listPipelines(sandbox.ldio!.id)
    expect(ingest.pipeline.outputs?.[0].config?.endpoint).toBe(sandbox.eventStreamUrl)

    await api.sendToPipeline(sandbox.ldio!.id, 'sandbox-ingest', '<a> <b> <c> .', 'text/turtle')

    const [statistics] = await api.getStreamStatistics(serverId)
    expect(statistics.ingestedMembers).toBe(1)
    const root = await api.getFragment(serverId, 'sandbox-parcels', 'by-page')
    expect(root.url).toBe('http://sandbox-ldes-server:8080/sandbox-parcels/by-page')
  })

  it('leaves a running sandbox as it is when started again', async () => {
    const before = await api.getSandbox()

    const after = await api.startSandbox()

    expect(after.ldesServer?.id).toBe(before.ldesServer?.id)
    expect((await api.listInstances('LDES_SERVER')).filter((s) => s.sandbox)).toHaveLength(1)
  })

  it('stops the sandbox', async () => {
    await api.stopSandbox()

    expect(await api.getSandbox()).toMatchObject({ state: 'STOPPED', ldesServer: null, ldio: null })
    expect((await api.listInstances('LDIO')).map((ldio) => ldio.name)).not.toContain('Sandbox LDIO')
  })

  it('only sends to pipelines with an HTTP input', async () => {
    await expect(api.sendToPipeline(IDS.consumerLdio, 'water-quality-replication', '{}',
      'application/json')).rejects.toMatchObject({ status: 400 })
  })

  it('publishes the server catalog with the datasets that have DCAT', async () => {
    expect((await api.getServerCatalog(IDS.sandboxServer)).catalogId).toBeNull()
    expect(await api.getCatalog(IDS.sandboxServer)).toBe('')

    const saved = await api.saveServerCatalog(IDS.sandboxServer, `
      @prefix dcat: <http://www.w3.org/ns/dcat#> .
      @prefix dcterms: <http://purl.org/dc/terms/> .
      [] a dcat:Catalog ; dcterms:title "Experiments"@en .`)

    expect(saved.catalogId).toBeTruthy()
    const published = await api.getCatalog(IDS.sandboxServer)
    expect(published).toContain('<http://sandbox-ldes-server:8080> a dcat:Catalog')
    expect(published).toContain('"Experiments"@en')
    expect(published).not.toContain('dcat:Dataset')

    await api.updateDcat(IDS.sandboxServer, { kind: 'eventStream', collection: 'sandbox-parcels' },
      '<http://sandbox-ldes-server:8080/sandbox-parcels> a <http://www.w3.org/ns/dcat#Dataset> .')

    expect(await api.getCatalog(IDS.sandboxServer))
      .toContain('<http://sandbox-ldes-server:8080/sandbox-parcels> a dcat:Dataset')
  })

  it('wants the server catalog as a blank node', async () => {
    await expect(api.saveServerCatalog(IDS.production, `
      @prefix dcat: <http://www.w3.org/ns/dcat#> .
      <http://example.org/catalog> a dcat:Catalog .`)).rejects.toMatchObject({ status: 400 })
  })

  it('returns copies, like an HTTP response', async () => {
    const [first] = await api.listInstances('LDIO')
    first.name = 'changed'

    expect((await api.listInstances('LDIO'))[0].name).not.toBe('changed')
  })
})
