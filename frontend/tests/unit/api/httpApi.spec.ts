import { ApiError } from '@/api/LdesAdminApi'
import { HttpLdesAdminApi } from '@/api/http/HttpLdesAdminApi'

interface Call {
  url: string
  method: string
  headers: Record<string, string>
  body?: string
}

/** What the backend answers, as far as the client looks at it */
function answer(status: number, body: unknown = '', statusText = '') {
  const text = typeof body === 'string' ? body : JSON.stringify(body)
  return { ok: status >= 200 && status < 300, status, statusText, text: async () => text, json: async () => JSON.parse(text) }
}

describe('HTTP LDES-admin API', () => {
  const api = new HttpLdesAdminApi('/api/v1')
  let calls: Call[]
  let next: ReturnType<typeof answer> | Error

  beforeEach(() => {
    calls = []
    next = answer(200, {})
    globalThis.fetch = jest.fn(async (url: string, init: RequestInit) => {
      calls.push({ url, method: init.method ?? 'GET', headers: init.headers as Record<string, string>, body: init.body as string })
      if (next instanceof Error) throw next
      return next
    }) as unknown as typeof fetch
  })

  it('reads JSON', async () => {
    next = answer(200, [{ id: '1', name: 'Production' }])

    const servers = await api.listInstances('LDES_SERVER')

    expect(servers).toEqual([{ id: '1', name: 'Production' }])
    expect(calls[0]).toMatchObject({ url: '/api/v1/ldes-servers', method: 'GET', headers: { Accept: 'application/json' } })
  })

  it('sends RDF as it is', async () => {
    next = answer(201, { collection: 'parcels' })

    await api.createEventStream('s1', '<parcels> a <EventStream> .')

    expect(calls[0]).toMatchObject({
      url: '/api/v1/ldes-servers/s1/eventstreams',
      method: 'POST',
      headers: { 'Content-Type': 'text/turtle' },
      body: '<parcels> a <EventStream> .',
    })
  })

  it('asks for a fragment by the IRI the server published', async () => {
    await api.getFragment('s1', 'sandbox-parcels', 'by-page',
      'http://sandbox-ldes-server:8080/sandbox-parcels/by-page?pageNumber=2')

    expect(calls[0].url).toBe('/api/v1/ldes-servers/s1/eventstreams/sandbox-parcels/views/by-page/fragment'
      + '?url=http%3A%2F%2Fsandbox-ldes-server%3A8080%2Fsandbox-parcels%2Fby-page%3FpageNumber%3D2')
  })

  it('asks for the root of a view without a URL', async () => {
    await api.getFragment('s1', 'parcels', 'by-page')

    expect(calls[0].url).toBe('/api/v1/ldes-servers/s1/eventstreams/parcels/views/by-page/fragment')
  })

  it('sends a message to a pipeline in its own content type', async () => {
    next = answer(202)

    await api.sendToPipeline('l1', 'parcels', '{"capakey": "1"}', 'application/json')

    expect(calls[0]).toMatchObject({
      url: '/api/v1/ldios/l1/pipelines/parcels/members',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"capakey": "1"}',
    })
  })

  it('reads the status of a halted pipeline', async () => {
    next = answer(200, { pipeline: 'parcels', status: 'HALTED' })

    expect(await api.haltPipeline('l1', 'parcels')).toBe('HALTED')
  })

  it('reports the problem details of the backend', async () => {
    next = answer(502, { status: 502, detail: "LDES server 'Legacy' could not list event streams" })

    await expect(api.listEventStreams('s1')).rejects.toEqual(
      new ApiError(502, "LDES server 'Legacy' could not list event streams"))
  })

  it('reports a backend that does not answer', async () => {
    next = new TypeError('Failed to fetch')

    await expect(api.getSandbox()).rejects.toMatchObject({ status: 0 })
  })
})
