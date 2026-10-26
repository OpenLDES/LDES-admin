import { type CronExpression, nextTime, parseCron } from '@/pipeline/cron'

/** Local times, so the tests don't depend on the time zone; away from daylight saving changes */
const at = (day: string, time = '00:00:00') => new Date(`${day}T${time}`)

function cron(expression: string): CronExpression {
  const parsed = parseCron(expression)
  if (!parsed.ok) throw new Error(parsed.error)
  return parsed.cron
}

describe('Spring cron expressions', () => {
  it.each([
    ['*/10 * * * * *', at('2026-11-02', '14:05:07'), at('2026-11-02', '14:05:10')],
    ['0 */5 * * * *', at('2026-11-02', '14:05:07'), at('2026-11-02', '14:10:00')],
    ['30 0 * * * *', at('2026-11-02', '14:00:30'), at('2026-11-02', '15:00:30')],
    ['@hourly', at('2026-11-02', '14:05:07'), at('2026-11-02', '15:00:00')],
    ['@daily', at('2026-11-30', '14:05:07'), at('2026-12-01')],
    ['0 0 9-17 * * MON-FRI', at('2026-11-06', '18:00:00'), at('2026-11-09', '09:00:00')],
    ['0 0 12 1,15 JAN,JUL *', at('2026-11-02'), at('2027-01-01', '12:00:00')],
    ['0 15/20 * * * *', at('2026-11-02', '14:36:00'), at('2026-11-02', '14:55:00')],
  ])('fires %s after %s at %s', (expression, after, expected) => {
    expect(nextTime(cron(expression), after)).toEqual(expected)
  })

  it('needs a day to match both the day of the month and of the week, as in Spring', () => {
    // The next Friday the 13th
    expect(nextTime(cron('0 0 0 13 * FRI'), at('2026-10-01'))).toEqual(at('2026-11-13'))
  })

  it('counts Sunday as 0 and as 7', () => {
    const sunday = at('2026-11-01', '12:00:00')

    expect(cron('* * * * * 0').matchesDay(sunday)).toBe(true)
    expect(cron('* * * * * 7').matchesDay(sunday)).toBe(true)
    expect(cron('* * * * * SUN-TUE').matchesDay(sunday)).toBe(true)
    expect(cron('* * * * * MON').matchesDay(sunday)).toBe(false)
  })

  it.each([
    ['0 0 0 L * *', at('2026-11-10'), at('2026-11-30')],
    ['0 0 0 L-2 * *', at('2026-11-10'), at('2026-11-28')],
    // 28 February 2026 is a Saturday
    ['0 0 0 LW * *', at('2026-02-10'), at('2026-02-27')],
    // 15 November is a Sunday: the Monday after
    ['0 0 0 15W * *', at('2026-11-02'), at('2026-11-16')],
    // 1 August is a Saturday: not the Friday before, in July, but the Monday after
    ['0 0 0 1W * *', at('2026-07-15'), at('2026-08-03')],
    ['0 0 12 * * 5L', at('2026-11-02'), at('2026-11-27', '12:00:00')],
    ['0 0 12 * * MON#2', at('2026-11-02'), at('2026-11-09', '12:00:00')],
  ])('understands the Quartz extensions: %s', (expression, after, expected) => {
    expect(nextTime(cron(expression), after)).toEqual(expected)
  })

  it('never fires on a day that does not exist', () => {
    expect(nextTime(cron('0 0 0 30 2 *'), at('2026-11-02'))).toBeUndefined()
  })

  it.each([
    ['* * * * *', 'Enter 6 fields: second minute hour day month weekday, e.g. */10 * * * * *'],
    ['61 * * * * *', 'The second goes from 0 to 59, not 61'],
    ['x * * * * *', "'x' is not a valid second"],
    ['*/0 * * * * *', "The step in '*/0' must be a whole number of 1 or more"],
    ['0 0 17-9 * * *', "The range '17-9' of the hour must go from low to high"],
    ['0 0 0 ? 13 *', 'The month goes from 1 to 12, not 13'],
    ['0 0 0 L-31 * *', "The offset in 'L-31' goes up to 30"],
    ['0 0 0 * * MON#6', "The ordinal in 'MON#6' goes from 1 to 5"],
  ])('explains what is wrong with %s', (expression, error) => {
    expect(parseCron(expression)).toEqual({ ok: false, error })
  })
})
