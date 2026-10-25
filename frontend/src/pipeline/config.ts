import type { ComponentConfig } from '@/domain/ldio'
import {
  componentInfo,
  type ComponentInfo,
  type ConfigProblem,
  type ConfigProperty,
  type ConfigReader,
  findConfigEntry,
  normalizeKey,
} from './catalog'

/**
 * Reading, changing and validating the configuration of a pipeline component, as described by its
 * properties in the catalog. The sandbox edits it in a form and as YAML; both change the same
 * configuration.
 */

export interface HttpHeader {
  key: string
  value: string
}

export function isBlank(value: unknown): boolean {
  return value === undefined || value === null || (typeof value === 'string' && value.trim() === '')
    || (Array.isArray(value) && value.length === 0)
}

/** The value of a property, as configured; undefined when it is not */
export function readProperty(config: ComponentConfig | undefined, key: string): unknown {
  return findConfigEntry(config, key)?.value
}

/** The value of a property as text: what is configured, or else its default */
export function effectiveValue(property: ConfigProperty | undefined, value: unknown):
  string | undefined {
  if (!isBlank(value) && typeof value !== 'object') return String(value)
  return property?.default
}

/** A list property as strings; a single value counts as a list of one */
export function asList(value: unknown): string[] {
  if (isBlank(value)) return []
  return Array.isArray(value) ? value.map((item) => (item === null ? '' : String(item))) : [String(value)]
}

export function asHeaders(value: unknown): HttpHeader[] {
  return Array.isArray(value)
    ? value.map((item) => ({
      key: String((item as Partial<HttpHeader>)?.key ?? ''),
      value: String((item as Partial<HttpHeader>)?.value ?? ''),
    }))
    : []
}

export function asBoolean(value: unknown): boolean | undefined {
  if (typeof value === 'boolean') return value
  if (typeof value === 'string' && /^(true|false)$/i.test(value.trim())) {
    return value.trim().toLowerCase() === 'true'
  }
  return undefined
}

/**
 * Sets a property, or removes it when the value is blank. A property that is already configured
 * keeps its place and spelling; a new one is nested along the dots of its key. Returns a new
 * configuration.
 */
export function writeProperty(config: ComponentConfig | undefined, property: ConfigProperty,
  value: unknown): ComponentConfig {
  const result = structuredCopy(config ?? {})
  const existing = findConfigEntry(result, property.key)
  const stored = toStored(property, value)
  if (existing) {
    const parent = existing.path.slice(0, -1)
      .reduce((target, key) => target[key] as ComponentConfig, result)
    const last = existing.path[existing.path.length - 1]
    if (stored === undefined) {
      delete parent[last]
      removeEmptyParents(result, existing.path.slice(0, -1))
    } else {
      parent[last] = stored
    }
    return result
  }
  if (stored === undefined) return result
  const path = property.key.split('.')
  let target = result
  for (const segment of path.slice(0, -1)) {
    // An object with an equivalent key may exist already, e.g. rdfWriter for rdf-writer
    const match = Object.keys(target).find((key) => normalizeKey(key) === normalizeKey(segment)
      && isObject(target[key]))
    if (match) {
      target = target[match] as ComponentConfig
    } else {
      target[segment] = {}
      target = target[segment] as ComponentConfig
    }
  }
  target[path[path.length - 1]] = stored
  return result
}

/** Removes a key that is not a property, given its path as returned by {@link unknownKeys} */
export function removeKey(config: ComponentConfig, path: string[]): ComponentConfig {
  const result = structuredCopy(config)
  const parent = path.slice(0, -1).reduce((target, key) => target[key] as ComponentConfig, result)
  delete parent[path[path.length - 1]]
  removeEmptyParents(result, path.slice(0, -1))
  return result
}

/** What the configuration stores for a value entered in the editor */
function toStored(property: ConfigProperty, value: unknown): unknown {
  switch (property.type) {
    case 'urls': {
      const list = asList(value)
      if (list.length === 0) return undefined
      return property.single && list.length === 1 ? list[0] : list
    }
    case 'headers': {
      const headers = asHeaders(value)
      return headers.length ? headers : undefined
    }
    case 'boolean':
      return typeof value === 'boolean' ? value : (asBoolean(value) ?? (isBlank(value) ? undefined : value))
    case 'integer':
      if (typeof value === 'number') return value
      if (isBlank(value)) return undefined
      return /^-?\d+$/.test(String(value).trim()) ? Number(String(value).trim()) : value
    case 'map':
      return isBlank(value) ? undefined : value
    default:
      return isBlank(value) ? undefined : value
  }
}

/**
 * Whether a property applies, given the other properties: e.g. the API key only matters for API
 * key authentication. Properties that are configured anyway always show.
 */
export function isApplicable(info: ComponentInfo, config: ComponentConfig | undefined,
  property: ConfigProperty): boolean {
  if (!property.when) return true
  const condition = info.properties.find((candidate) => candidate.key === property.when!.key)
  const value = effectiveValue(condition, readProperty(config, property.when.key))
  return value !== undefined && property.when.values.includes(value)
}

// Validation -----------------------------------------------------------------------------------

const DURATION = /^P(?!$)(\d+Y)?(\d+M)?(\d+W)?(\d+D)?(T(?=\d)(\d+H)?(\d+M)?(\d+(\.\d+)?S)?)?$/
const CRON_MACROS = ['@yearly', '@annually', '@monthly', '@weekly', '@daily', '@midnight', '@hourly']
const CRON_FIELD = /^[\d*?/,LW#A-Za-z-]+$/
const IRI = /^[A-Za-z][A-Za-z0-9+.-]*:[^\s<>"{}|\\^`]+$/
const CONTENT_TYPE = /^[\w.+-]+\/[\w.+-]+(\s*;.*)?$/

export function isHttpUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname !== ''
  } catch {
    return false
  }
}

/**
 * What is wrong with the value of a property, apart from it being missing. Problems about one
 * item of a list carry its index.
 */
export function valueProblems(property: ConfigProperty, value: unknown): ConfigProblem[] {
  if (isBlank(value)) return []
  const problem = (message: string, index?: number, severity: ConfigProblem['severity'] = 'error') =>
    ({ key: property.key, index, severity, message })
  const text = typeof value === 'object' ? '' : String(value)
  switch (property.type) {
    case 'url':
      return isHttpUrl(text.trim()) ? [] : [problem('Enter an http(s) URL, e.g. https://example.org/ldes')]
    case 'urls': {
      const list = asList(value)
      return list.flatMap((item, index) => {
        if (item.trim() === '') return [problem('Enter a URL, or remove this line', index)]
        if (!isHttpUrl(item.trim())) return [problem('Not an http(s) URL', index)]
        return list.indexOf(item) < index ? [problem('Listed twice', index, 'warning')] : []
      })
    }
    case 'iri':
      if (/^<.*>$/.test(text.trim())) return [problem('Leave out the angle brackets')]
      return IRI.test(text.trim()) ? [] : [problem('Enter an absolute IRI, e.g. https://example.org/Thing')]
    case 'propertyPath':
      return propertyPathProblem(text) ? [problem(propertyPathProblem(text)!)] : []
    case 'integer': {
      if (!/^-?\d+$/.test(text.trim())) return [problem('Enter a whole number')]
      return property.min !== undefined && Number(text) < property.min
        ? [problem(`Enter ${property.min} or more`)] : []
    }
    case 'boolean':
      return asBoolean(value) === undefined ? [problem('Either true or false')] : []
    case 'duration':
      return DURATION.test(text.trim()) ? [] : [problem('Enter an ISO 8601 duration, e.g. PT1M or PT30S')]
    case 'cron': {
      const fields = text.trim().split(/\s+/)
      const valid = CRON_MACROS.includes(text.trim())
        || (fields.length === 6 && fields.every((field) => CRON_FIELD.test(field)))
      return valid ? [] : [problem('Enter 6 fields: second minute hour day month weekday, e.g. */10 * * * * *')]
    }
    case 'select': {
      if (property.custom) {
        return CONTENT_TYPE.test(text.trim()) || !property.options?.some((o) => o.value.includes('/'))
          ? [] : [problem('Enter a content type, e.g. application/json')]
      }
      return property.options?.some((option) => option.value === text)
        ? [] : [problem(`Choose one of ${property.options?.map((o) => o.value).join(', ')}`)]
    }
    case 'context': {
      const trimmed = text.trim()
      if (trimmed.startsWith('{')) {
        let parsed: unknown
        try {
          parsed = JSON.parse(trimmed)
        } catch (e) {
          return [problem(`Not valid JSON: ${(e as Error).message}`)]
        }
        return isObject(parsed) && '@context' in parsed
          ? [] : [problem('An inline context needs an @context entry: {"@context": {...}}')]
      }
      return isHttpUrl(trimmed) ? [] : [problem('Enter the URL of a context, or a context document {"@context": {...}}')]
    }
    case 'json':
      // A frame written as a YAML map is fine too
      if (isObject(value)) return []
      try {
        return isObject(JSON.parse(text)) ? [] : [problem('Enter a JSON object')]
      } catch (e) {
        return [problem(`Not valid JSON: ${(e as Error).message}`)]
      }
    case 'headers':
      return asHeaders(value).flatMap((header, index) =>
        header.key.trim() && header.value.trim() ? [] : [problem('A header needs a name and a value', index)])
    case 'map':
      return isObject(value) ? [] : [problem('Expected a map of properties')]
    default:
      if (typeof value === 'object') return [problem('Expected a single value, not a list or map')]
      return property.pattern && !property.pattern.regex.test(text)
        ? [problem(property.pattern.message)] : []
  }
}

function propertyPathProblem(text: string): string | undefined {
  const iris = [...text.matchAll(/<([^<>]*)>/g)]
  if (iris.length === 0) return 'Enter IRIs in angle brackets, e.g. <https://example.org/name>'
  const invalid = iris.find((match) => !IRI.test(match[1]))
  if (invalid) return `<${invalid[1]}> is not an absolute IRI`
  const outside = text.replace(/<[^<>]*>/g, '').replace(/[\s/|^*+?()!]/g, '')
  return outside ? 'Only IRIs in angle brackets, joined with /, | or ^' : undefined
}

/**
 * Everything wrong with the configuration of a component: missing and invalid properties, rules
 * between properties, and keys that are not properties of the component.
 */
export function configProblems(componentId: string, config: ComponentConfig | undefined):
  ConfigProblem[] {
  const info = componentInfo(componentId)
  if (!info) return []
  const problems: ConfigProblem[] = []
  for (const property of info.properties) {
    const value = readProperty(config, property.key)
    if (isBlank(value)) {
      if (property.required && isApplicable(info, config, property)) {
        problems.push({ key: property.key, severity: 'error', message: 'Required' })
      }
      continue
    }
    problems.push(...valueProblems(property, value))
    if (!isApplicable(info, config, property)) {
      problems.push({
        key: property.key,
        severity: 'warning',
        message: `Only used when ${property.when!.key} is ${property.when!.values.join(' or ')}`,
      })
    }
  }
  if (info.check) problems.push(...info.check(readerOf(info, config)))
  for (const path of unknownKeys(info, config)) {
    problems.push({
      key: path.join('.'),
      severity: 'warning',
      message: `Not a property of ${info.label}; LDIO ignores it`,
    })
  }
  return problems
}

export function readerOf(info: ComponentInfo, config: ComponentConfig | undefined): ConfigReader {
  return {
    value: (key) => readProperty(config, key),
    effective: (key) => effectiveValue(info.properties.find((p) => p.key === key),
      readProperty(config, key)),
  }
}

/**
 * The keys of a configuration that are not properties of the component, e.g. a typo such as
 * sourceFormatt. Each is a path of keys, as in the configuration.
 */
export function unknownKeys(info: ComponentInfo, config: ComponentConfig | undefined): string[][] {
  if (!config) return []
  const properties = info.properties.map((property) => property.key.split('.').map(normalizeKey))
  const unknown: string[][] = []
  const visit = (object: ComponentConfig, path: string[], segments: string[]) => {
    for (const [key, value] of Object.entries(object)) {
      const keySegments = [...segments, ...key.split('.').map(normalizeKey)]
      const isProperty = properties.some((property) => sameSegments(property, keySegments))
      if (isProperty) continue
      const isPrefix = properties.some((property) => property.length > keySegments.length
        && sameSegments(property.slice(0, keySegments.length), keySegments))
      if (isPrefix && isObject(value)) {
        visit(value, [...path, key], keySegments)
      } else {
        unknown.push([...path, key])
      }
    }
  }
  visit(config, [], [])
  return unknown
}

function sameSegments(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((segment, i) => segment === b[i])
}

function removeEmptyParents(config: ComponentConfig, path: string[]) {
  for (let depth = path.length; depth > 0; depth--) {
    const parent = path.slice(0, depth - 1).reduce((target, key) => target[key] as ComponentConfig, config)
    const key = path[depth - 1]
    if (isObject(parent[key]) && Object.keys(parent[key] as object).length === 0) delete parent[key]
    else return
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** A deep copy of plain data; structuredClone is missing in the test environment */
function structuredCopy<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}
