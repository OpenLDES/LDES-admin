import { DataFactory, Parser, Writer } from 'n3'
import type { BlankNode, Literal, NamedNode, Quad, Quad_Object, Quad_Subject } from 'n3'
import type { ComponentConfig, ComponentDefinition, Pipeline } from '@/domain/ldio'
import type {
  PayloadFormat,
  SimulationInput,
  SimulationResult,
  SimulationStep,
  StepStatus,
} from '@/domain/sandbox'
import { componentInfo, type ComponentKind, configValue } from './catalog'

/**
 * Shows how a member flows through a pipeline, step by step, without an LDIO.
 *
 * The RDF handling is real (parsed and serialised with N3.js), the behaviour of every component is
 * a faithful approximation of what LDIO does. SPARQL queries and RML mappings are not evaluated.
 */
const { namedNode, literal, quad, blankNode } = DataFactory

const NS = {
  rdf: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
  rdfs: 'http://www.w3.org/2000/01/rdf-schema#',
  xsd: 'http://www.w3.org/2001/XMLSchema#',
  dcterms: 'http://purl.org/dc/terms/',
  prov: 'http://www.w3.org/ns/prov#',
  geo: 'http://www.opengis.net/ont/geosparql#',
  geojson: 'https://purl.org/geojson/vocab#',
}
const RDF_TYPE = `${NS.rdf}type`
const BASE_IRI = 'http://example.org/'

interface Model {
  quads: Quad[]
  prefixes: Record<string, string>
}

type TransformResult =
  | { status: 'ok'; model: Model; summary: string }
  | { status: 'filtered' | 'error'; summary: string }

export class PipelineSimulator {
  /** Members seen by change detection filters, per pipeline */
  private readonly seen = new Map<string, Set<string>>()

  constructor(private readonly now: () => Date = () => new Date()) {}

  run(pipeline: Pipeline, input: SimulationInput): SimulationResult {
    const steps: SimulationStep[] = []
    const inputInfo = componentInfo(pipeline.input.name)
    let model: Model

    steps.push(step('input', 0, pipeline.input, 'ok', receive(pipeline, input), input.content,
      formatOf(input.contentType)))
    try {
      if (pipeline.input.adapter) {
        const adapted = adapt(pipeline.input.adapter, input)
        model = adapted.model
        steps.push(step('adapter', 0, pipeline.input.adapter, 'ok', adapted.summary,
          toTurtle(model), 'turtle'))
      } else if (inputInfo?.requiresAdapter) {
        steps[0] = { ...steps[0], status: 'error', summary: `${inputInfo.label} needs an adapter to turn raw data into linked data` }
        return { steps, delivered: false, members: [] }
      } else {
        model = parseRdf(input.content, input.contentType)
        steps[0] = { ...steps[0], outputs: [toTurtle(model)], format: 'turtle' }
      }
    } catch (e) {
      const failing = pipeline.input.adapter ?? pipeline.input
      const kind = pipeline.input.adapter ? 'adapter' : 'input'
      if (kind === 'input') steps.pop()
      steps.push(step(kind, 0, failing, 'error', `Could not read the member: ${message(e)}`,
        undefined, 'text'))
      return { steps, delivered: false, members: [] }
    }

    for (const [index, transformer] of (pipeline.transformers ?? []).entries()) {
      const result = this.transform(pipeline.name, transformer, model)
      if (result.status !== 'ok') {
        steps.push(step('transformer', index, transformer, result.status, result.summary,
          undefined, 'text'))
        return { steps, delivered: false, members: [] }
      }
      model = result.model
      steps.push(step('transformer', index, transformer, 'ok', result.summary, toTurtle(model),
        'turtle'))
    }

    const outputs = pipeline.outputs ?? []
    outputs.forEach((output, index) => {
      const contentType = String(configValue(output.config, 'rdf-writer.content-type')
        ?? 'text/turtle')
      steps.push(step('output', index, output, 'ok', deliver(output, contentType),
        serialize(model, contentType), contentType.includes('json') ? 'json' : 'turtle'))
    })
    return { steps, delivered: outputs.length > 0, members: outputs.length > 0 ? [toTurtle(model)] : [] }
  }

  /** Forgets what change detection filters have seen */
  reset(): void {
    this.seen.clear()
  }

  private transform(pipeline: string, transformer: ComponentDefinition, model: Model):
    TransformResult {
    const config = transformer.config ?? {}
    switch (transformer.name) {
      case 'Ldio:VersionObjectCreator':
        return createVersion(model, config, this.now())
      case 'Ldio:VersionMaterialiser':
        return materialise(model, config)
      case 'Ldio:GeoJsonToWktTransformer':
        return geoJsonToWkt(model)
      case 'Ldio:SkolemisationTransformer':
        return skolemise(model, config)
      case 'Ldio:ChangeDetectionFilter': {
        const key = canonical(model.quads)
        const seen = this.seen.get(pipeline) ?? new Set<string>()
        this.seen.set(pipeline, seen)
        if (seen.has(key)) {
          return { status: 'filtered', summary: 'Unchanged since the last time: filtered out' }
        }
        seen.add(key)
        return { status: 'ok', model, summary: 'Not seen before: passed on' }
      }
      case 'Ldio:HttpEnricher':
        return enrich(model, config)
      case 'Ldio:SparqlConstructTransformer':
        return {
          status: 'ok',
          model,
          summary: isIdentityQuery(String(config.query ?? ''))
            ? 'CONSTRUCT { ?s ?p ?o } copied the model'
            : 'Queries are only evaluated by LDIO; the preview passes the model on unchanged',
        }
      default:
        return { status: 'ok', model, summary: 'Passed on' }
    }
  }
}

// Inputs and adapters -------------------------------------------------------------------------

function receive(pipeline: Pipeline, input: SimulationInput): string {
  const config = pipeline.input.config ?? {}
  switch (pipeline.input.name) {
    case 'Ldio:HttpIn':
      return `Received POST /${pipeline.name} (${input.contentType})`
    case 'Ldio:HttpInPoller':
      return `Polled ${input.source ?? firstOf(configValue(config, 'url')) ?? 'the API'}: 200 OK`
    case 'Ldio:LdesClient':
      return `Fetched a member from ${firstOf(configValue(config, 'urls')) ?? 'the LDES'}`
    case 'Ldio:LdesClientConnector':
      return 'Fetched a member through the dataspace connector'
    case 'Ldio:KafkaIn':
      return `Consumed a message from topic ${configValue(config, 'topics') ?? ''}`.trim()
    case 'Ldio:AmqpIn':
      return `Consumed a message from queue ${configValue(config, 'queue') ?? ''}`.trim()
    default:
      return 'Received a message'
  }
}

function adapt(adapter: ComponentDefinition, input: SimulationInput): {
  model: Model;
  summary: string
} {
  const config = adapter.config ?? {}
  switch (adapter.name) {
    case 'Ldio:JsonToLdAdapter': {
      const context = configValue(config, 'context')
      const model = jsonToModel(JSON.parse(input.content), contextOf(context))
      return {
        model,
        summary: `Applied JSON-LD context ${typeof context === 'string' && !context.trim().startsWith('{') ? context : '(inline)'}: ${model.quads.length} triples`,
      }
    }
    case 'Ldio:RmlAdapter': {
      const model = input.contentType.includes('csv')
        ? csvToModel(input.content)
        : jsonToModel(JSON.parse(input.content), plainContext('http://example.org/ns#'))
      return {
        model,
        summary: `Mapped with ${configValue(config, 'mapping') ?? 'the RML mapping'} (approximated): ${model.quads.length} triples`,
      }
    }
    case 'Ldio:NgsiV2ToLdAdapter': {
      const model = ngsiToModel(JSON.parse(input.content),
        String(configValue(config, 'data-identifier') ?? 'data'))
      return { model, summary: `Converted NGSI-v2 to NGSI-LD: ${model.quads.length} triples` }
    }
    default: {
      const model = parseRdf(input.content, input.contentType)
      return { model, summary: `Parsed ${model.quads.length} triples (${input.contentType})` }
    }
  }
}

function parseRdf(content: string, contentType: string): Model {
  if (contentType.includes('json')) {
    return jsonToModel(JSON.parse(content), plainContext(BASE_IRI))
  }
  const format = contentType.includes('n-quads') ? 'N-Quads'
    : contentType.includes('n-triples') ? 'N-Triples' : 'Turtle'
  const prefixes: Record<string, string> = {}
  const quads = new Parser({ format, baseIRI: BASE_IRI }).parse(content, null,
    (prefix, node) => {
      prefixes[prefix] = node.value
    })
  return { quads, prefixes }
}

/**
 * Plain JSON or simple JSON-LD to triples: every object becomes a node, every key a property, as
 * the terms of the context define it or else in its vocabulary.
 */
function jsonToModel(json: unknown, defaultContext: JsonLdContext): Model {
  const quads: Quad[] = []
  const roots = Array.isArray(json) ? json : [json]
  let prefixes = defaultContext.prefixes
  roots.forEach((root) => {
    const context = isObject(root) && root['@context'] !== undefined ? contextOf(root['@context']) : defaultContext
    prefixes = { ...prefixes, ...context.prefixes }
    toNode(root, context, quads)
  })
  return { quads, prefixes }
}

function toNode(value: unknown, context: JsonLdContext, quads: Quad[]): Quad_Subject {
  const object = isObject(value) ? value : {}
  const id = object['@id'] ?? object.id
  const subject = typeof id === 'string' && isIri(id) ? namedNode(expandIri(id, context)) : blankNode()
  const type = object['@type'] ?? object.type
  for (const value of [type].flat()) {
    if (typeof value === 'string') {
      quads.push(quad(subject, namedNode(RDF_TYPE), namedNode(expandTerm(value, context))))
    }
  }
  for (const [key, raw] of Object.entries(object)) {
    if (['@id', '@type', '@context', 'id', 'type'].includes(key)) continue
    const predicate = namedNode(expandTerm(key, context))
    const coercion = context.terms[key]?.type
    for (const item of [raw].flat()) {
      const term = toObject(item, context, quads, coercion)
      if (term) quads.push(quad(subject, predicate, term))
    }
  }
  return subject
}

function toObject(value: unknown, context: JsonLdContext, quads: Quad[], coercion?: string):
  Quad_Object | undefined {
  if (value === null || value === undefined) return undefined
  if (isGeometry(value)) {
    return literal(JSON.stringify(value), namedNode(`${NS.geojson}geometry`))
  }
  if (isObject(value)) return toNode(value, context, quads) as BlankNode | NamedNode
  if (coercion === '@id') return namedNode(expandIri(String(value), context))
  if (coercion) return literal(String(value), namedNode(coercion))
  if (typeof value === 'number') {
    return literal(String(value), namedNode(`${NS.xsd}${Number.isInteger(value) ? 'integer' : 'decimal'}`))
  }
  if (typeof value === 'boolean') return literal(String(value), namedNode(`${NS.xsd}boolean`))
  const text = String(value)
  if (/^https?:\/\/\S+$/.test(text)) return namedNode(text)
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text)) {
    return literal(text, namedNode(`${NS.xsd}dateTime`))
  }
  return literal(text)
}

function csvToModel(csv: string): Model {
  const [header, ...rows] = csv.trim().split(/\r?\n/).map((line) => line.split(','))
  const quads: Quad[] = []
  rows.forEach((row, index) => {
    const subject = namedNode(`http://example.org/row/${index + 1}`)
    header.forEach((column, i) => {
      if (row[i] !== undefined) {
        quads.push(quad(subject, namedNode(`http://example.org/ns#${column.trim()}`),
          literal(row[i].trim())))
      }
    })
  })
  return { quads, prefixes: { ex: 'http://example.org/ns#' } }
}

function ngsiToModel(json: unknown, dataIdentifier: string): Model {
  const entities = isObject(json) && Array.isArray(json[dataIdentifier]) ? json[dataIdentifier]
    : [json].flat()
  const vocabulary = 'https://uri.etsi.org/ngsi-ld/default-context/'
  const quads: Quad[] = []
  ;(entities as unknown[]).filter(isObject).forEach((entity) => {
    const subject = namedNode(`urn:ngsi-ld:${entity.type}:${entity.id}`)
    quads.push(quad(subject, namedNode(RDF_TYPE), namedNode(vocabulary + entity.type)))
    Object.entries(entity)
      .filter(([key]) => key !== 'id' && key !== 'type')
      .forEach(([key, attribute]) => {
        const value = isObject(attribute) ? attribute.value : attribute
        const term = toObject(value, plainContext(vocabulary), quads)
        if (term) quads.push(quad(subject, namedNode(vocabulary + key), term))
      })
  })
  return { quads, prefixes: { 'ngsi-ld': vocabulary } }
}

// Transformers --------------------------------------------------------------------------------

function createVersion(model: Model, config: ComponentConfig, now: Date): TransformResult {
  const memberType = configValue(config, 'member-type')
  if (!memberType) {
    return { status: 'error', summary: "'member-type' is not configured" }
  }
  const member = model.quads.find((q) => q.predicate.value === RDF_TYPE
    && q.object.value === memberType)?.subject
  if (!member || member.termType !== 'NamedNode') {
    return {
      status: 'error',
      summary: `No named subject of type <${memberType}>: the member is dropped`,
    }
  }
  const observedPath = configValue(config, 'date-observed-property')
  const observed = observedPath ? follow(model.quads, member, String(observedPath)) : undefined
  const timestamp = observed ?? now.toISOString()
  const delimiter = String(configValue(config, 'delimiter') ?? '/')
  const version = namedNode(`${member.value}${delimiter}${timestamp}`)
  const versionOf = String(configValue(config, 'versionOf-property') ?? `${NS.dcterms}isVersionOf`)
  const generatedAt = String(configValue(config, 'generatedAt-property')
    ?? `${NS.prov}generatedAtTime`)
  const quads = model.quads.map((q) => q.subject.equals(member)
    ? quad(version, q.predicate, q.object, q.graph) : q)
  // A model is a set: a statement the state object holds already is not added again
  for (const added of [quad(version, namedNode(versionOf), member),
    quad(version, namedNode(generatedAt), literal(timestamp, namedNode(`${NS.xsd}dateTime`)))]) {
    if (!quads.some((q) => q.equals(added))) quads.push(added)
  }
  return {
    status: 'ok',
    model: { ...model, quads },
    summary: `Created version ${shorten(version.value)}${observed ? '' : ' (timestamp: now)'}`,
  }
}

function materialise(model: Model, config: ComponentConfig): TransformResult {
  const versionOf = String(configValue(config, 'versionOf-property') ?? `${NS.dcterms}isVersionOf`)
  const links = model.quads.filter((q) => q.predicate.value === versionOf)
  if (links.length === 0) {
    return { status: 'ok', model, summary: 'No version objects found: model unchanged' }
  }
  const states = new Map(links.map((q) => [q.subject.value, q.object as NamedNode]))
  const toState = <T extends Quad_Subject | Quad_Object>(term: T): T =>
    (states.get(term.value) as T | undefined) ?? term
  let quads = model.quads
    .filter((q) => q.predicate.value !== versionOf)
    .map((q) => quad(toState(q.subject), q.predicate, toState(q.object), q.graph))
  if (String(configValue(config, 'restrict-to-members')) === 'true') {
    const members = new Set([...states.values()].map((state) => state.value))
    quads = quads.filter((q) => members.has(q.subject.value) || q.subject.termType === 'BlankNode')
  }
  return {
    status: 'ok',
    model: { ...model, quads },
    summary: `Materialised ${states.size === 1 ? 'the state of' : `${states.size} states:`} ${[...states.values()].map((state) => shorten(state.value)).join(', ')}`,
  }
}

function geoJsonToWkt(model: Model): TransformResult {
  let converted = 0
  const quads = model.quads.map((q) => {
    const geometry = q.object.termType === 'Literal' ? parseGeometry(q.object.value) : undefined
    if (!geometry) return q
    converted++
    return quad(q.subject, namedNode(`${NS.geo}asWKT`),
      literal(toWkt(geometry), namedNode(`${NS.geo}wktLiteral`)), q.graph)
  })
  return {
    status: 'ok',
    model: { quads, prefixes: { ...model.prefixes, geo: NS.geo } },
    summary: converted > 0 ? `Converted ${converted} GeoJSON ${converted === 1 ? 'geometry' : 'geometries'} to WKT`
      : 'No GeoJSON geometries found: model unchanged',
  }
}

function skolemise(model: Model, config: ComponentConfig): TransformResult {
  const domain = String(configValue(config, 'skolemDomain') ?? '').replace(/\/+$/, '')
  if (!domain) {
    return { status: 'error', summary: "'skolemDomain' is not configured" }
  }
  const names = new Map<string, NamedNode>()
  const skolem = <T extends Quad_Subject | Quad_Object>(term: T): T => {
    if (term.termType !== 'BlankNode') return term
    if (!names.has(term.value)) {
      names.set(term.value, namedNode(`${domain}/.well-known/genid/${pseudoUuid(term.value + names.size)}`))
    }
    return names.get(term.value) as T
  }
  const quads = model.quads.map((q) => quad(skolem(q.subject), q.predicate, skolem(q.object), q.graph))
  return {
    status: 'ok',
    model: { ...model, quads },
    summary: names.size > 0 ? `Replaced ${names.size} blank ${names.size === 1 ? 'node' : 'nodes'}`
      : 'No blank nodes found: model unchanged',
  }
}

function enrich(model: Model, config: ComponentConfig): TransformResult {
  const subject = model.quads[0]?.subject
  if (!subject) return { status: 'ok', model, summary: 'Empty model: nothing to enrich' }
  const url = 'https://api.example.org/enrichment/' + encodeURIComponent(lastSegment(subject.value))
  const quads = [...model.quads, quad(subject, namedNode(`${NS.rdfs}seeAlso`), namedNode(url))]
  return {
    status: 'ok',
    model: { ...model, quads },
    summary: `${configValue(config, 'http-method-property-path') ?? 'GET'} ${url}: 200 OK (simulated)`,
  }
}

// Outputs -------------------------------------------------------------------------------------

/** What the output would do, as the backend says it in a dry run */
function deliver(output: ComponentDefinition, contentType: string): string {
  const config = output.config ?? {}
  switch (output.name) {
    case 'Ldio:ConsoleOut':
      return 'Printed to the console of the LDIO'
    case 'Ldio:HttpOut':
      return `Would POST ${contentType} to ${configValue(config, 'endpoint')}`
    case 'Ldio:HttpSparqlOut':
      return `Would POST an update to ${configValue(config, 'endpoint')}`
    case 'Ldio:RepositorySink':
      return `Would add the model to repository ${configValue(config, 'repository-id')} of ${configValue(config, 'sparql-host')}`
    case 'Ldio:KafkaOut':
      return `Would send it to topic ${configValue(config, 'topic')}`
    case 'Ldio:AmqpOut':
      return `Would send it to queue ${configValue(config, 'queue')}`
    case 'Ldio:LdioRdbOut':
      return `Would insert rows into table ${configValue(config, 'table-name')}`
    case 'Ldio:NoopOut':
      return 'Did nothing'
    default:
      return 'Would deliver it'
  }
}

function serialize(model: Model, contentType: string): string {
  if (contentType.includes('json')) return toJsonLd(model)
  if (contentType.includes('n-quads') || contentType.includes('n-triples')) {
    return write(model.quads, {}, contentType.includes('n-quads') ? 'N-Quads' : 'N-Triples')
  }
  return toTurtle(model)
}

/**
 * Turtle with prefixes, and blank nodes that are used once written inline.
 */
export function toTurtle(model: Model): string {
  const prefixes = usedPrefixes(model)
  const objectUses = new Map<string, number>()
  model.quads.forEach((q) => {
    if (q.object.termType === 'BlankNode') {
      objectUses.set(q.object.value, (objectUses.get(q.object.value) ?? 0) + 1)
    }
  })
  const inline = (term: Quad_Subject | Quad_Object) => term.termType === 'BlankNode'
    && objectUses.get(term.value) === 1
  const bySubject = new Map<string, Quad[]>()
  model.quads.forEach((q) => bySubject.set(q.subject.value,
    [...(bySubject.get(q.subject.value) ?? []), q]))

  const writer = new Writer({ prefixes })
  const objectOf = (q: Quad, depth: number): Quad_Object => inline(q.object) && depth < 8
    ? writer.blank((bySubject.get(q.object.value) ?? []).map((nested) => ({
      predicate: nested.predicate,
      object: objectOf(nested, depth + 1),
    })))
    : q.object
  for (const quads of bySubject.values()) {
    if (inline(quads[0].subject)) continue
    quads.forEach((q) => writer.addQuad(q.subject, q.predicate, objectOf(q, 0)))
  }
  let result = ''
  writer.end((_error, output: string) => {
    result = output
  })
  return result.trim()
}

function toJsonLd(model: Model): string {
  const prefixes = usedPrefixes(model)
  const compact = (iri: string) => {
    const [prefix, namespace] = Object.entries(prefixes)
      .find(([, ns]) => iri.startsWith(ns) && iri.length > ns.length) ?? []
    return prefix ? `${prefix}:${iri.slice(namespace!.length)}` : iri
  }
  const nodes = new Map<string, Record<string, unknown>>()
  model.quads.forEach((q) => {
    const id = q.subject.termType === 'BlankNode' ? `_:${q.subject.value}` : compact(q.subject.value)
    const node = nodes.get(id) ?? { '@id': id }
    nodes.set(id, node)
    if (q.predicate.value === RDF_TYPE) {
      node['@type'] = [...[node['@type'] ?? []].flat(), compact(q.object.value)]
      return
    }
    const key = compact(q.predicate.value)
    const value = q.object.termType === 'Literal'
      ? literalValue(q.object, compact)
      : { '@id': q.object.termType === 'BlankNode' ? `_:${q.object.value}` : compact(q.object.value) }
    node[key] = node[key] === undefined ? value : [...[node[key]].flat(), value]
  })
  return JSON.stringify({ '@context': prefixes, '@graph': [...nodes.values()] }, null, 2)
}

function literalValue(term: Literal, compact: (iri: string) => string): unknown {
  if (term.language) return { '@value': term.value, '@language': term.language }
  if (term.datatype.value === `${NS.xsd}string`) return term.value
  return { '@value': term.value, '@type': compact(term.datatype.value) }
}

function write(quads: Quad[], prefixes: Record<string, string>, format: string): string {
  const writer = new Writer({ prefixes, format })
  writer.addQuads(quads)
  let result = ''
  writer.end((_error, output: string) => {
    result = output
  })
  return result.trim()
}

// Helpers -------------------------------------------------------------------------------------

const wellKnownPrefixes: Record<string, string> = {
  rdf: NS.rdf,
  rdfs: NS.rdfs,
  xsd: NS.xsd,
  dcterms: NS.dcterms,
  prov: NS.prov,
  geo: NS.geo,
  geojson: NS.geojson,
  sosa: 'http://www.w3.org/ns/sosa/',
  schema: 'http://schema.org/',
  perceel: 'https://data.vlaanderen.be/ns/perceel#',
  adres: 'https://data.vlaanderen.be/ns/adres#',
  mobiliteit: 'https://data.vlaanderen.be/ns/mobiliteit#',
}

function usedPrefixes(model: Model): Record<string, string> {
  const candidates = { ...wellKnownPrefixes, ...model.prefixes }
  const iris = model.quads.flatMap((q) => [q.subject, q.predicate, q.object,
    ...(q.object.termType === 'Literal' ? [q.object.datatype] : [])])
    .filter((term) => term.termType === 'NamedNode')
    .map((term) => term.value)
  return Object.fromEntries(Object.entries(candidates)
    .filter(([prefix, namespace]) => prefix !== '' && iris.some((iri) => iri.startsWith(namespace)
      && /^[A-Za-z_][\w-]*$/.test(iri.slice(namespace.length)))))
}

function step(kind: ComponentKind, index: number, component: ComponentDefinition,
  status: StepStatus, summary: string, output: string | undefined, format: PayloadFormat):
  SimulationStep {
  return {
    kind,
    index,
    component: component.name,
    status,
    summary,
    outputs: output === undefined ? [] : [output],
    format,
    durationMs: 2 + (hash(component.name + index) % 38),
  }
}

/** Follows a property path like <a>/<b> (or a bare IRI) from a subject to a literal value */
function follow(quads: Quad[], subject: Quad_Subject, path: string): string | undefined {
  const properties = path.includes('<')
    ? [...path.matchAll(/<([^>]+)>/g)].map((match) => match[1])
    : [path.trim()]
  let current: Quad_Subject | Quad_Object | undefined = subject
  for (const property of properties) {
    const from: Quad_Subject | Quad_Object = current
    current = quads.find((q) => q.subject.equals(from) && q.predicate.value === property)?.object
    if (!current) return undefined
  }
  return current.termType === 'Literal' ? current.value : undefined
}

/**
 * What the simulator understands of a JSON-LD context: its vocabulary, prefixes, and terms with
 * their IRI and the type of their values (an IRI with @id, or a datatype).
 */
interface JsonLdContext {
  vocabulary: string
  prefixes: Record<string, string>
  terms: Record<string, { iri: string; type?: string }>
}

function plainContext(vocabulary: string): JsonLdContext {
  return { vocabulary, prefixes: {}, terms: {} }
}

function contextOf(context: unknown): JsonLdContext {
  if (typeof context === 'string' && context.trim().startsWith('{')) {
    try {
      return contextOf(JSON.parse(context))
    } catch {
      return plainContext(BASE_IRI)
    }
  }
  // A context document, as LDIO expects it: {"@context": {...}}
  if (isObject(context) && '@context' in context) return contextOf(context['@context'])
  if (typeof context === 'string' && isIri(context)) {
    return plainContext(`${context.replace(/\.jsonld?$/, '').replace(/[#/]+$/, '')}#`)
  }
  if (!isObject(context)) return plainContext(BASE_IRI)
  const result = plainContext(typeof context['@vocab'] === 'string' ? context['@vocab'] : BASE_IRI)
  const entries = Object.entries(context).filter(([key]) => !key.startsWith('@'))
  // Prefixes first: the terms may use them
  for (const [key, value] of entries) {
    if (typeof value === 'string' && /[#/]$/.test(value) && isIri(value)) result.prefixes[key] = value
  }
  for (const [key, value] of entries) {
    const definition = typeof value === 'string' ? { '@id': value } : isObject(value) ? value : {}
    const iri = definition['@id']
    if (typeof iri !== 'string' || iri.startsWith('@')) continue
    const type = definition['@type']
    result.terms[key] = {
      iri: expandIri(iri, result),
      type: type === '@id' ? '@id' : typeof type === 'string' ? expandIri(type, result) : undefined,
    }
  }
  return result
}

/** A key or a type: a term of the context, a compact or absolute IRI, or else in the vocabulary */
function expandTerm(term: string, context: JsonLdContext): string {
  if (context.terms[term]) return context.terms[term].iri
  return isIri(term) ? expandIri(term, context) : context.vocabulary + term
}

/** A compact IRI with a prefix of the context, e.g. sosa:Observation, else the IRI as it is */
function expandIri(iri: string, context: JsonLdContext): string {
  const [prefix, ...rest] = iri.split(':')
  return context.prefixes[prefix] !== undefined && !rest.join(':').startsWith('//')
    ? context.prefixes[prefix] + rest.join(':') : iri
}

function isIri(value: string): boolean {
  return /^[a-z][a-z0-9+.-]*:\S+$/i.test(value) && !/^\d/.test(value)
}

const geometryTypes = ['Point', 'LineString', 'Polygon', 'MultiPoint', 'MultiLineString',
  'MultiPolygon']

interface Geometry {
  type: string
  coordinates: unknown[]
}

function isGeometry(value: unknown): value is Geometry {
  return isObject(value) && typeof value.type === 'string' && geometryTypes.includes(value.type)
    && Array.isArray(value.coordinates)
}

function parseGeometry(text: string): Geometry | undefined {
  if (!text.trim().startsWith('{')) return undefined
  try {
    const value = JSON.parse(text)
    return isGeometry(value) ? value : undefined
  } catch {
    return undefined
  }
}

function toWkt(geometry: Geometry): string {
  const point = (c: unknown) => (c as number[]).join(' ')
  const line = (cs: unknown) => `(${(cs as unknown[]).map(point).join(', ')})`
  const polygon = (rings: unknown) => `(${(rings as unknown[]).map(line).join(', ')})`
  const c = geometry.coordinates
  switch (geometry.type) {
    case 'Point':
      return `POINT (${point(c)})`
    case 'LineString':
      return `LINESTRING ${line(c)}`
    case 'Polygon':
      return `POLYGON ${polygon(c)}`
    case 'MultiPoint':
      return `MULTIPOINT ${line(c)}`
    case 'MultiLineString':
      return `MULTILINESTRING (${c.map(line).join(', ')})`
    default:
      return `MULTIPOLYGON (${c.map(polygon).join(', ')})`
  }
}

function isIdentityQuery(query: string): boolean {
  return /CONSTRUCT\s*\{\s*\?s\s+\?p\s+\?o\s*\.?\s*\}\s*WHERE\s*\{\s*\?s\s+\?p\s+\?o\s*\.?\s*\}/i
    .test(query)
}

function canonical(quads: Quad[]): string {
  const labels = new Map<string, string>()
  const term = (t: Quad_Subject | Quad_Object) => t.termType === 'BlankNode'
    ? labels.get(t.value) ?? labels.set(t.value, `_:b${labels.size}`).get(t.value)!
    : t.termType === 'Literal' ? `"${t.value}"^^${t.datatype.value}@${t.language}` : t.value
  return quads.map((q) => `${term(q.subject)} ${q.predicate.value} ${term(q.object)}`).sort()
    .join('\n')
}

function formatOf(contentType: string): PayloadFormat {
  if (contentType.includes('json')) return 'json'
  if (contentType.includes('turtle') || contentType.includes('n-')) return 'turtle'
  return 'text'
}

function shorten(iri: string): string {
  return iri.length > 64 ? `…${iri.slice(-60)}` : iri
}

function lastSegment(iri: string): string {
  return iri.replace(/[#/]+$/, '').split(/[#/]/).pop() ?? iri
}

function firstOf(value: unknown): unknown {
  return Array.isArray(value) ? value[0] : value
}

function hash(text: string): number {
  let h = 0
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) >>> 0
  return h
}

function pseudoUuid(seed: string): string {
  const hex = Array.from({ length: 4 }, (_, i) => hash(seed + i).toString(16).padStart(8, '0'))
    .join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20, 32)}`
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function message(error: unknown): string {
  return (error instanceof Error ? error.message : String(error)).split('\n')[0]
}
