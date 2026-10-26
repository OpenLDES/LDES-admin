import type { Pipeline } from '@/domain/ldio'
import { describeSchedule, nextPoll, pollerOf } from '@/pipeline/poller'

function polling(config: Record<string, unknown>): Pipeline {
  return {
    name: 'polled',
    input: { name: 'Ldio:HttpInPoller', config, adapter: { name: 'Ldio:JsonToLdAdapter' } },
    outputs: [{ name: 'Ldio:ConsoleOut' }],
  }
}

describe('the HTTP In Poller of a pipeline', () => {
  it('is only there when the pipeline starts with one', () => {
    expect(pollerOf(undefined)).toBeUndefined()
    expect(pollerOf({ name: 'p', input: { name: 'Ldio:HttpIn' } })).toBeUndefined()
  })

  it('polls one URL or several', () => {
    expect(pollerOf(polling({ url: 'https://api.example.org/a', cron: '0 * * * * *' }))?.urls)
      .toEqual(['https://api.example.org/a'])
    expect(pollerOf(polling({ url: ['https://api.example.org/a', ' ', 'https://api.example.org/b'], cron: '0 * * * * *' }))?.urls)
      .toEqual(['https://api.example.org/a', 'https://api.example.org/b'])
  })

  it('sends its headers and API key along', () => {
    const poller = pollerOf(polling({
      url: 'https://api.example.org/a',
      http: { headers: [{ key: 'Accept', value: 'application/json' }] },
      auth: { type: 'API_KEY', 'api-key': 'secret' },
    }))

    expect(poller?.request).toEqual({ headers: { Accept: 'application/json', 'X-API-KEY': 'secret' } })
  })

  it('reads keys the way LDIO does', () => {
    const poller = pollerOf(polling({
      url: 'https://api.example.org/a',
      'auth.type': 'API_KEY',
      'auth.apiKeyHeader': 'Ocp-Apim-Subscription-Key',
      'auth.API_KEY': 'secret',
    }))

    expect(poller?.request.headers).toEqual({ 'Ocp-Apim-Subscription-Key': 'secret' })
  })

  it('gets an OAuth2 token with client credentials', () => {
    const poller = pollerOf(polling({
      url: 'https://api.example.org/a',
      auth: {
        type: 'OAUTH2_CLIENT_CREDENTIALS',
        'client-id': 'ldes-admin',
        'client-secret': 's3cr3t',
        'token-endpoint': 'https://login.example.org/token',
      },
    }))

    expect(poller?.request.clientCredentials).toEqual({
      tokenEndpoint: 'https://login.example.org/token',
      clientId: 'ldes-admin',
      clientSecret: 's3cr3t',
      scope: undefined,
    })
  })

  it('prefers the cron expression to the interval, like LDIO', () => {
    const schedule = pollerOf(polling({ url: 'https://api.example.org/a', cron: '*/10 * * * * *', interval: 'PT1M' }))?.schedule

    expect(schedule && describeSchedule(schedule)).toBe('cron */10 * * * * *')
    expect(schedule && nextPoll(schedule, new Date('2026-11-02T14:05:07')))
      .toEqual(new Date('2026-11-02T14:05:10'))
  })

  it('polls an interval after the previous poll', () => {
    const schedule = pollerOf(polling({ url: 'https://api.example.org/a', interval: 'PT1M30.5S' }))?.schedule

    expect(schedule).toEqual({ kind: 'interval', text: 'PT1M30.5S', milliseconds: 90_500 })
    expect(nextPoll(schedule!, new Date('2026-11-02T14:05:07'))).toEqual(new Date('2026-11-02T14:06:37.500'))
  })

  it.each([
    [{}, "Set 'cron' or 'interval' to poll on a schedule"],
    [{ cron: '0 0 25 * * *' }, 'The cron expression is not valid: The hour goes from 0 to 23, not 25'],
    // java.time.Duration has no weeks
    [{ interval: 'P1W' }, 'LDIO cannot poll every P1W: use days, hours, minutes and seconds, e.g. PT1M'],
    [{ interval: 'PT0S' }, 'LDIO cannot poll every PT0S: use days, hours, minutes and seconds, e.g. PT1M'],
  ])('explains why it cannot poll on schedule %j', (config, problem) => {
    const poller = pollerOf(polling({ url: 'https://api.example.org/a', ...config }))

    expect(poller?.schedule).toBeUndefined()
    expect(poller?.scheduleProblem).toBe(problem)
  })
})
