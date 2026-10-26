/**
 * Spring cron expressions, as LDIO schedules an HTTP In Poller with them: six fields (second,
 * minute, hour, day of the month, month, day of the week), the macros such as @hourly, and the
 * Quartz extensions L, W and #. Unlike Unix cron, a day must match both the day of the month and
 * the day of the week.
 *
 * See https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-cron-expression
 */
export interface CronExpression {
  readonly expression: string
  readonly seconds: ReadonlySet<number>
  readonly minutes: ReadonlySet<number>
  readonly hours: ReadonlySet<number>
  /** 1 to 12 */
  readonly months: ReadonlySet<number>
  /** Whether a day matches both the day of the month and the day of the week */
  readonly matchesDay: (date: Date) => boolean
}

export type CronParseResult = { ok: true; cron: CronExpression } | { ok: false; error: string }

const FIELDS_MESSAGE = 'Enter 6 fields: second minute hour day month weekday, e.g. */10 * * * * *'

const MACROS: Record<string, string> = {
  '@yearly': '0 0 0 1 1 *',
  '@annually': '0 0 0 1 1 *',
  '@monthly': '0 0 0 1 * *',
  '@weekly': '0 0 0 * * 0',
  '@daily': '0 0 0 * * *',
  '@midnight': '0 0 0 * * *',
  '@hourly': '0 0 * * * *',
}
const MONTH_NAMES = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']
/** Monday is 1, Sunday 7 (or 0) */
const DAY_NAMES = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

interface Field {
  name: string
  min: number
  max: number
}

const SECOND: Field = { name: 'second', min: 0, max: 59 }
const MINUTE: Field = { name: 'minute', min: 0, max: 59 }
const HOUR: Field = { name: 'hour', min: 0, max: 23 }
const DAY_OF_MONTH: Field = { name: 'day of the month', min: 1, max: 31 }
const MONTH: Field = { name: 'month', min: 1, max: 12 }
const DAY_OF_WEEK: Field = { name: 'day of the week', min: 0, max: 7 }

/** How far ahead to look: 29 February on a Monday can be decades away */
const HORIZON_MS = 30 * 366 * 24 * 3600 * 1000
const MAX_ATTEMPTS = 200_000

class CronError extends Error {}

export function parseCron(expression: string): CronParseResult {
  const trimmed = expression.trim()
  const fields = (MACROS[trimmed.toLowerCase()] ?? trimmed).split(/\s+/)
  if (fields.length !== 6) return { ok: false, error: FIELDS_MESSAGE }
  const [second, minute, hour, dayOfMonth, month, dayOfWeek] = fields
  try {
    const matchesDayOfMonth = parseDayOfMonth(dayOfMonth)
    const matchesDayOfWeek = parseDayOfWeek(dayOfWeek)
    return {
      ok: true,
      cron: {
        expression: trimmed,
        seconds: parseValues(second, SECOND),
        minutes: parseValues(minute, MINUTE),
        hours: parseValues(hour, HOUR),
        months: parseValues(replaceNames(month, MONTH_NAMES), MONTH),
        matchesDay: (date) => matchesDayOfMonth(date) && matchesDayOfWeek(date),
      },
    }
  } catch (e) {
    if (e instanceof CronError) return { ok: false, error: e.message }
    throw e
  }
}

/**
 * The first time after the given one at which the expression fires, in the time zone of the
 * browser; undefined when it does not fire in the coming decades (e.g. on 30 February).
 */
export function nextTime(cron: CronExpression, after: Date): Date | undefined {
  let time = new Date(after.getTime() - after.getMilliseconds() + 1000)
  const horizon = after.getTime() + HORIZON_MS
  for (let attempt = 0; attempt < MAX_ATTEMPTS && time.getTime() <= horizon; attempt++) {
    const [year, month, day] = [time.getFullYear(), time.getMonth(), time.getDate()]
    const [hour, minute] = [time.getHours(), time.getMinutes()]
    let next: Date
    if (!cron.months.has(month + 1)) next = new Date(year, month + 1, 1)
    else if (!cron.matchesDay(time)) next = new Date(year, month, day + 1)
    else if (!cron.hours.has(hour)) next = new Date(year, month, day, hour + 1)
    else if (!cron.minutes.has(minute)) next = new Date(year, month, day, hour, minute + 1)
    else if (!cron.seconds.has(time.getSeconds())) next = new Date(time.getTime() + 1000)
    else return time
    // A change to or from daylight saving time can turn a step backwards: always move on
    time = next.getTime() > time.getTime() ? next : new Date(time.getTime() + 1000)
  }
  return undefined
}

// Fields ---------------------------------------------------------------------------------------

/** A list of values, ranges and steps, e.g. 0,30 or 1-5 or *\/10 or 5/15 */
function parseValues(text: string, field: Field): Set<number> {
  const values = new Set<number>()
  for (const part of text.split(',')) {
    const slash = part.indexOf('/')
    const rangeText = slash === -1 ? part : part.slice(0, slash)
    let [min, max] = parseRange(rangeText, field)
    let step = 1
    if (slash !== -1) {
      const stepText = part.slice(slash + 1)
      if (!/^\d+$/.test(stepText) || Number(stepText) < 1) {
        throw new CronError(`The step in '${part}' must be a whole number of 1 or more`)
      }
      step = Number(stepText)
      // 5/15 counts from 5 to the end
      if (!rangeText.includes('-')) max = field.max
    }
    for (let value = min; value <= max; value += step) values.add(value)
  }
  return values
}

function parseRange(text: string, field: Field): [number, number] {
  if (text === '*') return [field.min, field.max]
  const hyphen = text.indexOf('-')
  if (hyphen === -1) {
    const value = parseValue(text, field)
    return [value, value]
  }
  let min = parseValue(text.slice(0, hyphen), field)
  const max = parseValue(text.slice(hyphen + 1), field)
  // Sunday is 7, but 0 at the start of a range, as in SUN-TUE
  if (field === DAY_OF_WEEK && min === 7) min = 0
  if (min > max) throw new CronError(`The range '${text}' of the ${field.name} must go from low to high`)
  return [min, max]
}

function parseValue(text: string, field: Field): number {
  if (!/^\d+$/.test(text)) throw new CronError(`'${text}' is not a valid ${field.name}`)
  const value = Number(text)
  if (value < field.min || value > field.max) {
    throw new CronError(`The ${field.name} goes from ${field.min} to ${field.max}, not ${value}`)
  }
  return value
}

/** JAN becomes 1, MON becomes 1, ... as Spring reads them */
function replaceNames(text: string, names: string[]): string {
  return names.reduce((result, name, index) => result.replaceAll(name, String(index + 1)),
    text.toUpperCase())
}

function parseDayOfMonth(text: string): (date: Date) => boolean {
  const value = text === '?' ? '*' : text.toUpperCase()
  if (/[LW]/.test(value)) return quartzDayOfMonth(value)
  const days = parseValues(value, DAY_OF_MONTH)
  return (date) => days.has(date.getDate())
}

/** L (the last day), L-3 (three days before it), LW (the last weekday), 15W (the weekday nearest to the 15th) */
function quartzDayOfMonth(value: string): (date: Date) => boolean {
  if (value === 'L') return (date) => date.getDate() === lastDay(date)
  if (value === 'LW') return (date) => date.getDate() === lastWeekday(date)
  const beforeLast = /^L-(\d+)$/.exec(value)
  if (beforeLast) {
    const offset = Number(beforeLast[1])
    if (offset > 30) throw new CronError(`The offset in '${value}' goes up to 30`)
    return (date) => date.getDate() === lastDay(date) - offset
  }
  const nearest = /^(\d+)W$/.exec(value)
  if (nearest) {
    const day = parseValue(nearest[1], DAY_OF_MONTH)
    return (date) => date.getDate() === nearestWeekday(date, day)
  }
  throw new CronError(`'${value}' is not a valid day of the month`)
}

function parseDayOfWeek(text: string): (date: Date) => boolean {
  const value = text === '?' ? '*' : replaceNames(text, DAY_NAMES)
  if (value.includes('#') || value.endsWith('L')) return quartzDayOfWeek(value, text)
  const days = parseValues(value, DAY_OF_WEEK)
  if (days.has(0)) days.add(7)
  return (date) => days.has(isoDay(date))
}

/** 5L (the last Friday of the month), 1#2 (its second Monday); text is how it was written */
function quartzDayOfWeek(value: string, text: string): (date: Date) => boolean {
  const last = /^(\d+)L$/.exec(value)
  if (last) {
    const day = isoOf(parseValue(last[1], DAY_OF_WEEK))
    return (date) => isoDay(date) === day && date.getDate() + 7 > lastDay(date)
  }
  const nth = /^(\d+)#(\d+)$/.exec(value)
  if (nth) {
    const day = isoOf(parseValue(nth[1], DAY_OF_WEEK))
    const ordinal = Number(nth[2])
    if (ordinal < 1 || ordinal > 5) throw new CronError(`The ordinal in '${text}' goes from 1 to 5`)
    return (date) => isoDay(date) === day && Math.ceil(date.getDate() / 7) === ordinal
  }
  throw new CronError(`'${text}' is not a valid day of the week`)
}

// Days -----------------------------------------------------------------------------------------

/** Monday 1 to Sunday 7 */
function isoDay(date: Date): number {
  return isoOf(date.getDay())
}

function isoOf(day: number): number {
  return day === 0 ? 7 : day
}

function lastDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
}

function lastWeekday(date: Date): number {
  const last = lastDay(date)
  const weekday = new Date(date.getFullYear(), date.getMonth(), last).getDay()
  return weekday === 6 ? last - 1 : weekday === 0 ? last - 2 : last
}

/** The weekday nearest to a day, without leaving the month */
function nearestWeekday(date: Date, day: number): number | undefined {
  const last = lastDay(date)
  if (day > last) return undefined
  const weekday = new Date(date.getFullYear(), date.getMonth(), day).getDay()
  if (weekday === 6) return day === 1 ? 3 : day - 1
  if (weekday === 0) return day === last ? day - 2 : day + 1
  return day
}
