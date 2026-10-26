import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { ApiError } from '@/api/LdesAdminApi'
import type { Pipeline } from '@/domain/ldio'
import { useSandboxStore } from '@/stores/sandbox'

const mockPoll = jest.fn()
jest.mock('@/api', () => ({
  ...jest.requireActual('@/api'),
  api: { poll: (...args: unknown[]) => mockPoll(...args) },
}))

const CHANGES = 'https://api.example.org/changes'
const MEMBER = '<http://example.org/parcel/1> <http://example.org/area> "512.4" .'

function polledPipeline(config: Record<string, unknown>): Pipeline {
  return {
    name: 'polled',
    input: { name: 'Ldio:HttpInPoller', config: { url: CHANGES, ...config }, adapter: { name: 'Ldio:RdfAdapter' } },
    outputs: [{ name: 'Ldio:ConsoleOut' }],
  }
}

describe('sandbox store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts with the default template', () => {
    const sandbox = useSandboxStore()

    sandbox.ensureInitialised()

    expect(sandbox.name).toBe('parcels-to-ldes')
    expect(sandbox.conversion.valid).toBe(true)
    expect(sandbox.yaml).toContain('name: Ldio:VersionObjectCreator')
  })

  it('adds components and only accepts valid connections', () => {
    const sandbox = useSandboxStore()
    sandbox.clear()
    const input = sandbox.addComponent('Ldio:LdesClient')
    const output = sandbox.addComponent('Ldio:ConsoleOut')

    expect(sandbox.connect(output.id, input.id)).toBe('An output ends the pipeline')
    expect(sandbox.connect(input.id, output.id)).toBeUndefined()
    expect(sandbox.conversion.valid).toBe(true)
    expect(sandbox.selectedId).toBe(output.id)
  })

  it('places new components below the pipeline', () => {
    const sandbox = useSandboxStore()
    sandbox.loadTemplate('http-to-console')

    const added = sandbox.addComponent('Ldio:NoopOut')

    expect(added.position).toEqual({ x: 0, y: 330 })
  })

  it('applies an edited component and removes nodes with their edges', () => {
    const sandbox = useSandboxStore()
    sandbox.loadTemplate('http-to-console')

    sandbox.updateComponent('output-1', 'Ldio:HttpOut', { endpoint: 'http://localhost:8080/x' })
    expect(sandbox.conversion.pipeline?.outputs).toEqual([
      { name: 'Ldio:HttpOut', config: { endpoint: 'http://localhost:8080/x' } },
    ])

    sandbox.removeNode('adapter')
    expect(sandbox.edges).toHaveLength(0)
    expect(sandbox.errors.map((issue) => issue.message)).toContain('HTTP In receives raw data and needs an adapter')
  })

  it('redraws the canvas from YAML, or explains what is wrong', () => {
    const sandbox = useSandboxStore()

    expect(sandbox.applyYaml('name: [')).toBeDefined()
    expect(sandbox.applyYaml(`name: from-yaml
input:
  name: Ldio:LdesClient
  config:
    urls: [http://localhost:8080/parcels]
outputs:
  - name: Ldio:ConsoleOut`)).toBeUndefined()
    expect(sandbox.name).toBe('from-yaml')
    expect(sandbox.nodes).toHaveLength(2)
  })

  it('runs a member through the pipeline, one node at a time', async () => {
    jest.useFakeTimers()
    const sandbox = useSandboxStore()
    sandbox.loadTemplate('http-to-console')

    const running = sandbox.run()
    expect(sandbox.running).toBe(true)
    await jest.runAllTimersAsync()
    await running

    expect(sandbox.running).toBe(false)
    expect(sandbox.result?.delivered).toBe(true)
    expect(sandbox.result?.steps.map((step) => step.component))
      .toEqual(['Ldio:HttpIn', 'Ldio:RdfAdapter', 'Ldio:ConsoleOut'])
    expect(sandbox.nodes.every((node) => node.data.runStatus === 'done')).toBe(true)
    jest.useRealTimers()
  })

  describe('with an HTTP In Poller', () => {
    beforeEach(() => {
      mockPoll.mockReset()
      mockPoll.mockResolvedValue({ contentType: 'text/turtle; charset=utf-8', body: MEMBER })
    })

    afterEach(() => {
      useSandboxStore().stopPolling()
      jest.useRealTimers()
    })

    it('fetches the input from the URL of the poller', async () => {
      const sandbox = useSandboxStore()
      sandbox.load(polledPipeline({ cron: '*/10 * * * * *', auth: { type: 'API_KEY', 'api-key': 'secret' } }))

      await sandbox.fetchInput()

      expect(mockPoll).toHaveBeenCalledWith({ url: CHANGES, headers: { 'X-API-KEY': 'secret' } })
      expect(sandbox.sample).toEqual({ content: MEMBER, contentType: 'text/turtle', source: CHANGES })
      expect(sandbox.lastPoll).toMatchObject({ url: CHANGES })
      expect(sandbox.lastPoll?.error).toBeUndefined()
    })

    it('tells why a fetch failed', async () => {
      mockPoll.mockRejectedValue(new ApiError(502, `${CHANGES} answered 404 NOT_FOUND`))
      const sandbox = useSandboxStore()
      sandbox.load(polledPipeline({ cron: '*/10 * * * * *' }), { content: 'kept', contentType: 'text/turtle' })

      await expect(sandbox.fetchInput()).rejects.toBeInstanceOf(ApiError)

      expect(sandbox.lastPoll?.error).toBe(`${CHANGES} answered 404 NOT_FOUND`)
      expect(sandbox.sample.content).toBe('kept')
    })

    it('polls when the cron expression fires, and runs every response through the pipeline', async () => {
      jest.useFakeTimers({ now: new Date('2026-11-02T14:05:07') })
      const sandbox = useSandboxStore()
      sandbox.load(polledPipeline({ cron: '*/10 * * * * *' }))

      sandbox.startPolling()
      expect(sandbox.nextPollAt).toEqual(new Date('2026-11-02T14:05:10'))

      // The poll at 14:05:10, and a run of three steps
      await jest.advanceTimersByTimeAsync(3000 + 3 * 600)
      expect(mockPoll).toHaveBeenCalledTimes(1)
      expect(sandbox.polls).toBe(1)
      expect(sandbox.result?.delivered).toBe(true)
      expect(sandbox.result?.steps[0].summary).toBe(`Polled ${CHANGES}: 200 OK`)
      expect(sandbox.nextPollAt).toEqual(new Date('2026-11-02T14:05:20'))

      await jest.advanceTimersByTimeAsync(10_000)
      expect(mockPoll).toHaveBeenCalledTimes(2)

      sandbox.stopPolling()
      await jest.advanceTimersByTimeAsync(60_000)
      expect(mockPoll).toHaveBeenCalledTimes(2)
      expect(sandbox.nextPollAt).toBeUndefined()
    })

    it('polls an interval right away, and keeps polling when a poll fails', async () => {
      jest.useFakeTimers({ now: new Date('2026-11-02T14:05:07') })
      mockPoll.mockRejectedValueOnce(new ApiError(502, `Could not poll ${CHANGES}: it is unreachable`))
      const sandbox = useSandboxStore()
      sandbox.load(polledPipeline({ interval: 'PT1M' }))

      sandbox.startPolling()
      await jest.advanceTimersByTimeAsync(0)

      expect(mockPoll).toHaveBeenCalledTimes(1)
      expect(sandbox.polls).toBe(0)
      expect(sandbox.lastPoll?.error).toBe(`Could not poll ${CHANGES}: it is unreachable`)
      expect(sandbox.polling).toBe(true)
      expect(sandbox.nextPollAt).toEqual(new Date('2026-11-02T14:06:07'))

      await jest.advanceTimersByTimeAsync(60_000)
      expect(sandbox.polls).toBe(1)
      expect(sandbox.lastPoll?.error).toBeUndefined()
    })

    it('follows changes to the schedule, and stops without a poller', async () => {
      jest.useFakeTimers({ now: new Date('2026-11-02T14:05:07') })
      const sandbox = useSandboxStore()
      sandbox.load(polledPipeline({ cron: '*/10 * * * * *' }))
      const input = sandbox.nodes.find((node) => node.data.kind === 'input')!
      sandbox.startPolling()

      sandbox.updateComponent(input.id, 'Ldio:HttpInPoller', { url: CHANGES, cron: '0 * * * * *' })
      await nextTick()
      expect(sandbox.nextPollAt).toEqual(new Date('2026-11-02T14:06:00'))

      sandbox.updateComponent(input.id, 'Ldio:HttpIn', {})
      await nextTick()
      expect(sandbox.polling).toBe(false)
      expect(sandbox.poller).toBeUndefined()
    })
  })
})
