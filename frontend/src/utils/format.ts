import { compact } from '@/domain/rdf'

const integer = new Intl.NumberFormat('en-US')
const compactNumber = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })
const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

export function formatNumber(value: number | undefined): string {
  return value === undefined ? '–' : integer.format(value)
}

/** 1,284 · 12.9K · 8.5M */
export function formatCompact(value: number | undefined): string {
  if (value === undefined) return '–'
  return value < 10_000 ? integer.format(value) : compactNumber.format(value)
}

export function formatPercent(value: number, digits = 1): string {
  return `${(value * 100).toFixed(digits).replace(/\.0+$/, '')}%`
}

export function formatRelative(iso: string | undefined, now = Date.now()): string {
  if (!iso) return '–'
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 45) return seconds <= 0 ? 'just now' : 'in a few seconds'
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute')
  if (abs < 86_400) return relative.format(Math.round(seconds / 3600), 'hour')
  return relative.format(Math.round(seconds / 86_400), 'day')
}

export function formatDateTime(iso: string | undefined): string {
  if (!iso) return '–'
  return new Date(iso).toLocaleString('en-GB', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

export function formatDuration(ms: number | undefined): string {
  if (ms === undefined) return '–'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`
  return `${Math.floor(ms / 60_000)} min ${Math.round((ms % 60_000) / 1000)} s`
}

/** dcterms:created instead of http://purl.org/dc/terms/created, where a prefix is known */
export function shortIri(iri: string | undefined): string {
  if (!iri) return '–'
  const short = compact(iri)
  return short.startsWith('<') ? iri : short
}

/** The host and path of a URL, without the scheme */
export function shortUrl(url: string): string {
  return url.replace(/^https?:\/\//, '')
}
