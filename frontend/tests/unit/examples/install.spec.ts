import { MockLdesAdminApi } from '@/api/mock/MockLdesAdminApi'
import { IDS } from '@/api/mock/sampleData'
import type { Instance } from '@/domain/instance'
import { parking } from '@/examples/parking'
import { sensors } from '@/examples/sensors'
import { failed, type InstallRequest, installExample, plannedSteps } from '@/examples/install'

const ALL = { stream: true, views: true, pipeline: true, data: true }

describe('installing an example', () => {
  let api: MockLdesAdminApi
  let server: Instance
  let ldio: Instance

  beforeEach(async () => {
    api = new MockLdesAdminApi({ latency: 0, startupDelay: 0 })
    server = await api.startLocalInstance('LDES_SERVER', { name: 'Demo server' })
    ldio = await api.getInstance('LDIO', IDS.sandboxLdio)
  })

  const install = (request: Partial<InstallRequest>) => installExample(
    { example: sensors, server, ldio, parts: ALL, ...request }, { client: api, pollMs: 1 })
  const ingested = async (collection = 'sensor-observations') =>
    (await api.getStreamStatistics(server.id)).find((s) => s.collection === collection)?.ingestedMembers

  it('plans the steps it takes', () => {
    expect(plannedSteps({ example: sensors, server, ldio, parts: ALL }).map((step) => step.label)).toEqual([
      'Event stream sensor-observations',
      'Views by-page and by-time',
      'Pipeline sensor-observations-ingest on Sandbox LDIO',
      '36 observations of the last four hours, through the pipeline',
    ])
    expect(plannedSteps({ example: parking, server, parts: ALL }).map((step) => step.label))
      .toEqual(['Event stream parking-occupancy', 'Views by-page and by-time'])
  })

  it('creates the event stream and its views, deploys the pipeline and sends the sample data through it', async () => {
    const reported: string[][] = []

    const steps = await installExample({ example: sensors, server, ldio, parts: ALL },
      { client: api, pollMs: 1, onChange: (progress) => reported.push(progress.map((step) => step.status)) })

    expect(steps.map((step) => step.status)).toEqual(['done', 'done', 'done', 'done'])
    expect(reported[0]).toEqual(['running', 'pending', 'pending', 'pending'])
    expect((await api.listViews(server.id, 'sensor-observations')).map((view) => view.name))
      .toEqual(['by-page', 'by-time'])
    const pipeline = (await api.listPipelines(ldio.id)).find((p) => p.pipeline.name === 'sensor-observations-ingest')
    expect(pipeline?.pipeline.outputs?.[0].config?.endpoint).toBe(`${server.internalUrl}/sensor-observations`)
    expect(await ingested()).toBe(36)
  })

  it('ingests the sample data directly without an LDIO', async () => {
    const steps = await install({ ldio: undefined })

    expect(steps.map((step) => step.label)).toEqual(['Event stream sensor-observations',
      'Views by-page and by-time', '36 observations of the last four hours, ingested directly'])
    expect(failed(steps)).toBe(false)
    expect(await ingested()).toBe(36)
  })

  it('leaves what exists already as it is', async () => {
    await install({ parts: { ...ALL, data: false } })

    const steps = await install({ parts: { ...ALL, data: false } })

    expect(steps.map((step) => [step.status, step.detail])).toEqual([
      ['skipped', 'Exists already'],
      ['skipped', 'Exist already'],
      ['skipped', 'Deployed already'],
    ])
  })

  it('gives the pipeline another name when one of the same name feeds another server', async () => {
    const other = await api.startLocalInstance('LDES_SERVER', { name: 'Other server' })
    await install({ server: other, parts: { ...ALL, data: false } })

    const steps = await install({})

    expect(steps[2].label).toBe('Pipeline sensor-observations-ingest-2 on Sandbox LDIO')
    expect(await ingested()).toBe(36)
  })

  it('creates the parts the user asks for, under another name', async () => {
    await api.createEventStream(server.id, sensors.eventStream(server.url, 'air'))

    const steps = await install({ collection: 'air', parts: { stream: false, views: true, pipeline: false, data: true } })

    expect(steps.map((step) => step.part)).toEqual(['views', 'data'])
    expect(await ingested('air')).toBe(36)
  })

  it('stops at the first step that fails', async () => {
    const steps = await install({ server: await api.getInstance('LDES_SERVER', IDS.legacy) })

    expect(steps.map((step) => step.status)).toEqual(['failed', 'skipped', 'skipped', 'skipped'])
    expect(steps[0].detail).toContain('is unreachable')
    expect(failed(steps)).toBe(true)
  })

  it('lets the parking pipeline fetch its own data', async () => {
    const steps = await install({ example: parking })

    expect(steps.map((step) => step.part)).toEqual(['stream', 'views', 'pipeline'])
    await new Promise((resolve) => setTimeout(resolve, 5))
    expect(await ingested('parking-occupancy')).toBe(14)
  })
})
