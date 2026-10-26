import type { Pipeline } from '@/domain/ldio'
import type { PollRequest } from '@/domain/sandbox'
import { componentInfo, type ConfigReader } from './catalog'
import { asHeaders, asList, readerOf } from './config'
import { type CronExpression, nextTime, parseCron } from './cron'

/**
 * What the HTTP In Poller that starts a pipeline polls, and when. The sandbox polls the same, once
 * or on the schedule of the component, to simulate the pipeline with what it would receive.
 */
export const HTTP_IN_POLLER = 'Ldio:HttpInPoller'

export type PollSchedule =
  | { kind: 'cron'; cron: CronExpression }
  | { kind: 'interval'; text: string; milliseconds: number }

export interface Poller {
  urls: string[]
  /** What LDIO sends along with every request: its headers, the API key, OAuth2 client credentials */
  request: Omit<PollRequest, 'url'>
  schedule?: PollSchedule
  /** Why it cannot poll on a schedule, e.g. an invalid cron expression */
  scheduleProblem?: string
}

/** LDIO reads the interval with java.time.Duration: days, hours, minutes and seconds */
const JAVA_DURATION = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)(?:[.,](\d{1,9}))?S)?)?$/i

export function pollerOf(pipeline: Pipeline | undefined): Poller | undefined {
  const info = componentInfo(HTTP_IN_POLLER)
  if (pipeline?.input.name !== HTTP_IN_POLLER || !info) return undefined
  const config = readerOf(info, pipeline.input.config)
  return {
    urls: asList(config.value('url')).map((url) => url.trim()).filter((url) => url !== ''),
    request: requestOf(config),
    ...scheduleOf(config),
  }
}

/** When to poll next: when the cron expression fires, or an interval after the previous poll */
export function nextPoll(schedule: PollSchedule, after: Date): Date | undefined {
  return schedule.kind === 'cron'
    ? nextTime(schedule.cron, after)
    : new Date(after.getTime() + schedule.milliseconds)
}

export function describeSchedule(schedule: PollSchedule): string {
  return schedule.kind === 'cron' ? `cron ${schedule.cron.expression}` : `every ${schedule.text}`
}

function requestOf(config: ConfigReader): Omit<PollRequest, 'url'> {
  const headers: Record<string, string> = {}
  for (const header of asHeaders(config.value('http.headers'))) {
    if (header.key.trim() !== '') headers[header.key.trim()] = header.value
  }
  const auth = config.effective('auth.type')
  if (auth === 'API_KEY') {
    headers[config.effective('auth.api-key-header') ?? 'X-API-KEY'] = text(config.value('auth.api-key'))
  }
  if (auth !== 'OAUTH2_CLIENT_CREDENTIALS') return { headers }
  return {
    headers,
    clientCredentials: {
      tokenEndpoint: text(config.value('auth.token-endpoint')),
      clientId: text(config.value('auth.client-id')),
      clientSecret: text(config.value('auth.client-secret')),
      scope: text(config.value('auth.scope')) || undefined,
    },
  }
}

/** Like LDIO: a cron expression wins over the (deprecated) interval */
function scheduleOf(config: ConfigReader): Pick<Poller, 'schedule' | 'scheduleProblem'> {
  const cron = text(config.value('cron'))
  if (cron) {
    const parsed = parseCron(cron)
    return parsed.ok
      ? { schedule: { kind: 'cron', cron: parsed.cron } }
      : { scheduleProblem: `The cron expression is not valid: ${parsed.error}` }
  }
  const interval = text(config.value('interval'))
  if (!interval) return { scheduleProblem: "Set 'cron' or 'interval' to poll on a schedule" }
  const milliseconds = intervalMilliseconds(interval)
  return milliseconds
    ? { schedule: { kind: 'interval', text: interval, milliseconds } }
    : { scheduleProblem: `LDIO cannot poll every ${interval}: use days, hours, minutes and seconds, e.g. PT1M` }
}

function intervalMilliseconds(interval: string): number | undefined {
  const match = JAVA_DURATION.exec(interval)
  if (!match) return undefined
  const [days, hours, minutes, seconds] = match.slice(1, 5).map((part) => Number(part ?? 0))
  const fraction = Number(`0.${match[5] ?? 0}`)
  const milliseconds = ((((days * 24 + hours) * 60 + minutes) * 60) + seconds + fraction) * 1000
  return milliseconds > 0 ? Math.round(milliseconds) : undefined
}

function text(value: unknown): string {
  return value === undefined || value === null || typeof value === 'object' ? '' : String(value).trim()
}
