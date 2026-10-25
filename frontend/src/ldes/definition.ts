import { DataFactory } from 'n3'
import type { Quad_Object, Term } from 'n3'
import { compact, PREFIXES } from '@/domain/rdf'
import type { ConfigProblem } from '@/pipeline/catalog'
import { isBlank, isHttpUrl } from '@/pipeline/config'
import type { RdfProperty, ResourceKind, StreamContext } from './catalog'
import {
  addResource,
  declarePrefixFor,
  iriFromText,
  objectOf,
  objectsOf,
  predicatesOf,
  prefixedName,
  RDF_TYPE,
  type RdfGraph,
  type Resource,
  setObjects,
  termText,
} from './turtle'

/**
 * Reading, changing and validating the properties of a resource in a definition, e.g. the event
 * stream or a fragmentation of a view, as described by the catalog. The forms and the Turtle edit
 * the same graph.
 */
export interface DefinitionProblem extends ConfigProblem {
  /** The resource it is about; none for the definition as a whole */
  node?: Term
}

const { namedNode, literal } = DataFactory

/** The value of a property as a form shows it; undefined when it is not set */
export function readValue(graph: RdfGraph, node: Resource | undefined, property: RdfProperty):
  string | undefined {
  const object = node && objectOf(graph, node, property.predicate)
  return object ? termText(graph, object) : undefined
}

/**
 * Sets a property to what is entered in a form, or removes it when that is blank. A literal keeps
 * its datatype, unless the server only accepts the datatype of the property.
 *
 * @param order the predicates of the resource in the order the definition lists them
 */
export function writeValue(graph: RdfGraph, node: Resource, property: RdfProperty, value: unknown,
  order: string[] = []): RdfGraph {
  if (isBlank(value)) return setObjects(graph, node, property.predicate, [], order)
  const text = property.type === 'string' ? String(value) : String(value).trim()
  if (!property.datatype) {
    const declared = iriFromText(graph, text)
    return setObjects(declared.graph, node, property.predicate, [namedNode(declared.iri)], order)
  }
  const current = objectOf(graph, node, property.predicate)
  const datatype = !property.strictDatatype && current?.termType === 'Literal' && !current.language
    ? current.datatype.value
    : property.datatype
  const object = datatype === `${PREFIXES.xsd}string` ? literal(text) : literal(text, namedNode(datatype))
  // Turtle writes strings, integers and booleans without their datatype
  const plain = ['string', 'integer', 'boolean'].map((name) => PREFIXES.xsd + name).includes(datatype)
  return setObjects(plain ? graph : declarePrefixFor(graph, datatype), node, property.predicate, [object], order)
}

/** The predicates of a resource in the order of its properties, its type first */
export function propertyOrder(properties: RdfProperty[]): string[] {
  return [RDF_TYPE, ...properties.map((property) => property.predicate)]
}

/** A new resource of a kind, e.g. a time-based fragmentation, with its initial values */
export function createResource(graph: RdfGraph, kind: ResourceKind, stream: StreamContext = {}):
  { graph: RdfGraph; node: Resource } {
  const created = addResource(declarePrefixFor(graph, kind.type), [[RDF_TYPE, namedNode(kind.type)]])
  let result = created.graph
  for (const [key, value] of Object.entries(kind.initial(stream))) {
    const property = kind.properties.find((candidate) => candidate.key === key)!
    result = writeValue(result, created.node, property, value, propertyOrder(kind.properties))
  }
  return { graph: result, node: created.node }
}

/**
 * Turns a resource into another kind, e.g. a time-based fragmentation into a geospatial one: the
 * properties both kinds have stay, the others go, and missing ones get their initial values.
 */
export function changeKind(graph: RdfGraph, node: Resource, from: ResourceKind | undefined,
  to: ResourceKind, stream: StreamContext = {}): RdfGraph {
  let result = setObjects(declarePrefixFor(graph, to.type), node, RDF_TYPE, [namedNode(to.type)])
  for (const property of from?.properties ?? []) {
    if (!to.properties.some((candidate) => candidate.predicate === property.predicate)) {
      result = setObjects(result, node, property.predicate, [])
    }
  }
  for (const [key, value] of Object.entries(to.initial(stream))) {
    const property = to.properties.find((candidate) => candidate.key === key)!
    if (!objectOf(result, node, property.predicate)) {
      result = writeValue(result, node, property, value, propertyOrder(to.properties))
    }
  }
  return result
}

/** The kind of a resource, after its rdf:type */
export function kindOf(graph: RdfGraph, node: Resource, kinds: ResourceKind[]): ResourceKind | undefined {
  const type = objectOf(graph, node, RDF_TYPE)?.value
  return kinds.find((kind) => kind.type === type)
}

/** Whether a property applies, given the others, e.g. the version delimiter only with createVersions */
export function isApplicable(graph: RdfGraph, node: Resource | undefined, properties: RdfProperty[],
  property: RdfProperty): boolean {
  if (!property.when) return true
  const condition = properties.find((candidate) => candidate.key === property.when!.key)
  const value = (condition && readValue(graph, node, condition)) ?? condition?.default
  return value !== undefined && property.when.values.includes(value)
}

/**
 * The predicates of a resource that are none of its properties, e.g. a typo such as
 * tree:maxGranularty, as prefixed names where possible.
 *
 * @param others predicates that belong to the resource without being a property in the form
 */
export function unknownPredicates(graph: RdfGraph, node: Resource | undefined, properties: RdfProperty[],
  others: string[] = []): string[] {
  if (!node) return []
  const known = new Set([RDF_TYPE, ...others, ...properties.map((property) => property.predicate)])
  return predicatesOf(graph, node).filter((predicate) => !known.has(predicate))
}

/** A predicate as a key in problems and forms: its prefixed name where possible */
export function predicateKey(graph: RdfGraph, predicate: string): string {
  return prefixedName(graph, predicate) ?? predicate
}

// Validation -----------------------------------------------------------------------------------

/** Names of event streams and views: the server takes the local name of the IRI */
const NAME = /^[A-Za-z_]([\w.-]*[\w-])?$/

/** xsd:duration, which has no weeks */
const XSD_DURATION = /^-?P(?!$)(\d+Y)?(\d+M)?(\d+D)?(T(?=\d)(\d+H)?(\d+M)?(\d+(\.\d+)?S)?)?$/
const CONTENT_TYPE = /^[\w.+-]+\/[\w.+-]+(\s*;.*)?$/
const SCHEME = /^([A-Za-z][\w+.-]*):/

/** What is wrong with the name of an event stream or a view, if anything */
export function nameProblem(name: string): string | undefined {
  if (name === '') return 'Required'
  if (/^\d/.test(name)) return 'Start with a letter: the server would drop leading digits'
  return NAME.test(name) ? undefined : 'Use letters, digits, -, _ and . only, starting with a letter'
}

/** The last segment of an IRI, which the server takes as name */
export function localName(iri: string): string {
  return iri.slice(Math.max(iri.lastIndexOf('/'), iri.lastIndexOf('#')) + 1)
}

/**
 * Everything wrong with the properties of a resource: missing and invalid values, values of
 * properties that do not apply, and predicates that are not properties of it.
 *
 * @param owner what the resource is, for messages, e.g. "a time-based fragmentation"
 * @param others predicates that belong to the resource without being a property in the form
 */
export function resourceProblems(graph: RdfGraph, node: Resource, properties: RdfProperty[], owner: string,
  others: string[] = []): DefinitionProblem[] {
  const problems: DefinitionProblem[] = []
  for (const property of properties) {
    const values = objectsOf(graph, node, property.predicate)
    const add = (message: string, severity: ConfigProblem['severity'] = 'error') =>
      problems.push({ node, key: property.key, severity, message })
    if (values.length === 0) {
      if (property.required && isApplicable(graph, node, properties, property)) add('Required')
      continue
    }
    for (const problem of valueProblems(graph, property, values)) add(problem.message, problem.severity)
    if (!isApplicable(graph, node, properties, property)) {
      add(`Only allowed when ${property.when!.key} is ${property.when!.values.join(' or ')}`)
    }
  }
  for (const predicate of unknownPredicates(graph, node, properties, others)) {
    problems.push({
      node,
      key: predicateKey(graph, predicate),
      severity: 'warning',
      message: `Not a property of ${owner}; the server ignores it`,
    })
  }
  return problems
}

function valueProblems(graph: RdfGraph, property: RdfProperty, values: Quad_Object[]):
  Omit<ConfigProblem, 'key'>[] {
  const error = (message: string) => [{ severity: 'error' as const, message }]
  if (values.length > 1) return error(`Set it once; there are ${values.length} values`)
  const [value] = values
  if (!property.datatype) {
    if (value.termType === 'Literal') return error(`Must be an IRI, not the literal "${value.value}"`)
    if (value.termType !== 'NamedNode') return error('Must be an IRI')
    if (property.type === 'url') {
      return isHttpUrl(value.value) ? [] : error('Enter an http(s) URL, e.g. https://example.org')
    }
    const scheme = SCHEME.exec(value.value)?.[1]
    if (!scheme) return error('Enter a prefixed name such as dcterms:created, or an absolute IRI')
    if (!value.value.includes('//') && !['urn', 'tag', 'mailto', 'did'].includes(scheme.toLowerCase())) {
      return error(`Unknown prefix ${scheme}: declare it in the definition, or enter the full IRI`)
    }
    return []
  }
  if (value.termType !== 'Literal') {
    return error(`Must be a literal, not ${value.termType === 'NamedNode' ? 'an IRI' : 'a blank node'}`)
  }
  if (property.strictDatatype && value.language) return error('Must be a plain string, without a language tag')
  if (property.strictDatatype && value.datatype.value !== property.datatype) {
    const name = (iri: string) => prefixedName(graph, iri) ?? compact(iri)
    return error(`Must be typed ${name(property.datatype)}, not ${name(value.datatype.value)}`)
  }
  const text = value.value
  switch (property.type) {
    case 'integer': {
      if (!/^[+-]?\d+$/.test(text)) return error('Enter a whole number')
      const number = Number(text)
      if ((property.min !== undefined && number < property.min) || (property.max !== undefined && number > property.max)) {
        return error(property.max === undefined ? `Enter ${property.min} or more`
          : `Enter a number from ${property.min ?? 0} to ${property.max}`)
      }
      return []
    }
    case 'duration':
      return XSD_DURATION.test(text) ? [] : error('Enter an xsd:duration, e.g. P30D, PT12H or P1Y2M (no weeks)')
    case 'boolean':
      return /^(true|false)$/.test(text) ? [] : error('Either true or false')
    case 'select':
      if (property.custom) {
        return property.options?.some((option) => option.value.includes('/')) && !CONTENT_TYPE.test(text)
          ? error('Enter a content type, e.g. text/turtle') : []
      }
      return property.options?.some((option) => option.value === text)
        ? [] : error(`Choose one of ${property.options?.map((option) => option.value).join(', ')}`)
    default:
      if (text === '') return error('Must not be empty')
      if (property.pattern && !property.pattern.regex.test(text)) return error(property.pattern.message)
      if (property.regex) {
        try {
          new RegExp(text)
        } catch (e) {
          return [{ severity: 'warning', message: `Not a valid regular expression: ${(e as Error).message}` }]
        }
      }
      return []
  }
}

/**
 * Everything wrong with the retention policies of a view or an event source: the server knows two
 * kinds, and takes at most one of each.
 */
export function retentionPolicyProblems(graph: RdfGraph, policies: Resource[], kinds: ResourceKind[]):
  DefinitionProblem[] {
  const problems: DefinitionProblem[] = []
  policies.forEach((policy, index) => {
    const kind = kindOf(graph, policy, kinds)
    if (!kind) {
      problems.push({
        node: policy,
        severity: 'error',
        message: `Not a retention policy the server knows: use ${kinds.map((k) => predicateKey(graph, k.type)).join(' or ')}`,
      })
      return
    }
    if (policies.slice(0, index).some((other) => kindOf(graph, other, kinds) === kind)) {
      problems.push({
        node: policy,
        severity: 'error',
        message: `There can be only one ${kind.label.toLowerCase()} retention policy`,
      })
    }
    problems.push(...resourceProblems(graph, policy, kind.properties,
      `a ${kind.label.toLowerCase()} retention policy`))
  })
  return problems
}

export function hasErrors(problems: ConfigProblem[]): boolean {
  return problems.some((problem) => problem.severity === 'error')
}
