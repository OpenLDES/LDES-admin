import { DataFactory } from 'n3'
import type { NamedNode, Quad, Quad_Graph, Quad_Object, Quad_Subject } from 'n3'
import { PREFIXES } from '@/domain/rdf'

/**
 * Reads JSON-LD into quads, without a JSON-LD library: the compact JSON-LD people write and tools
 * produce. It knows inline contexts (prefixes, @vocab, @base, @language, terms with @id, @type and
 * a @list, @set or @language container), node objects with @id and @type, @graph, value objects,
 * {"@id"} references and @list. A remote context (a URL) cannot be read in the browser.
 */
const { namedNode, blankNode, literal, quad, defaultGraph } = DataFactory

const XSD = PREFIXES.xsd
const RDF = PREFIXES.rdf

interface TermDefinition {
  id: string
  type?: string
  container?: string
  language?: string | null
}

interface Context {
  base?: string
  vocab?: string
  language?: string
  terms: Map<string, TermDefinition>
}

export interface JsonLdGraph {
  quads: Quad[]
  /** The prefixes of the context, to write the quads with */
  prefixes: Record<string, string>
}

export function readJsonLd(text: string): JsonLdGraph {
  let json: unknown
  try {
    json = JSON.parse(text)
  } catch (e) {
    throw new Error(`Not valid JSON: ${(e as Error).message}`)
  }
  const reader = new Reader()
  const roots = Array.isArray(json) ? json : [json]
  const prefixes: Record<string, string> = {}
  for (const root of roots) {
    if (!isObject(root)) throw new Error('JSON-LD holds objects, not ' + JSON.stringify(root))
    const context = withContext(emptyContext(), root['@context'])
    for (const [term, definition] of context.terms) {
      if (/[/#:]$/.test(definition.id) && !definition.type && !definition.container) {
        prefixes[term] = definition.id
      }
    }
    reader.document(root, context)
  }
  return { quads: reader.quads, prefixes }
}

class Reader {
  readonly quads: Quad[] = []

  /** The context of the document is already the active one */
  document(root: Record<string, unknown>, context: Context) {
    const { '@context': _, ...node } = root
    // A document that only holds @graph: its nodes are in the default graph
    if (Object.keys(node).length === 1 && node['@graph'] !== undefined) {
      for (const item of asArray(node['@graph'])) this.node(item, context, defaultGraph())
    } else {
      this.node(node, context, defaultGraph())
    }
  }

  node(value: unknown, outer: Context, graph: Quad_Graph): Quad_Subject {
    if (!isObject(value)) throw new Error('Expected a node object, not ' + JSON.stringify(value))
    const context = withContext(outer, value['@context'])
    const id = value['@id']
    const subject: Quad_Subject = typeof id === 'string' ? this.subject(id, context) : blankNode()
    for (const type of asArray(value['@type'])) {
      this.quads.push(quad(subject, namedNode(`${RDF}type`), namedNode(expandIri(String(type), context, true)), graph))
    }
    if (value['@graph'] !== undefined) {
      // A named graph: its nodes are in the graph named after this node
      const named = subject.termType === 'NamedNode' ? subject : blankNode()
      for (const node of asArray(value['@graph'])) this.node(node, context, named)
    }
    for (const [key, raw] of Object.entries(value)) {
      if (key.startsWith('@')) continue
      const definition = context.terms.get(key)
      const predicate = expandIri(key, context, true)
      // As JSON-LD does, a key that does not map to an IRI is dropped
      if (!isAbsoluteIri(predicate)) continue
      for (const object of this.objects(raw, definition, context, graph)) {
        this.quads.push(quad(subject, namedNode(predicate), object, graph))
      }
    }
    return subject
  }

  private objects(raw: unknown, definition: TermDefinition | undefined, context: Context,
    graph: Quad_Graph): Quad_Object[] {
    if (raw === null || raw === undefined) return []
    if (definition?.container === '@list') return [this.list(asArray(raw), definition, context, graph)]
    if (definition?.container === '@language' && isObject(raw) && !('@value' in raw)) {
      return Object.entries(raw).flatMap(([language, texts]) =>
        asArray(texts).map((text) => literal(String(text), language)))
    }
    return asArray(raw).flatMap((item) => {
      const object = this.object(item, definition, context, graph)
      return object ? [object] : []
    })
  }

  private object(item: unknown, definition: TermDefinition | undefined, context: Context,
    graph: Quad_Graph): Quad_Object | undefined {
    if (item === null) return undefined
    if (isObject(item)) {
      if ('@value' in item) return this.value(item, context)
      if ('@list' in item) return this.list(asArray(item['@list']), definition, context, graph)
      if (Object.keys(item).length === 1 && typeof item['@id'] === 'string') {
        return this.subject(item['@id'], context) as NamedNode
      }
      return this.node(item, context, graph) as Quad_Object
    }
    const type = definition?.type
    if (type === '@id' && typeof item === 'string') return this.subject(item, context) as NamedNode
    if (type === '@vocab' && typeof item === 'string') return namedNode(expandIri(item, context, true))
    if (typeof item === 'string') {
      if (type) return literal(item, namedNode(type))
      const language = definition?.language === undefined ? context.language : definition.language
      return language ? literal(item, language) : literal(item)
    }
    if (typeof item === 'boolean') return literal(String(item), namedNode(type ?? `${XSD}boolean`))
    if (typeof item === 'number') {
      if (type) return literal(String(item), namedNode(type))
      return Number.isInteger(item) && Math.abs(item) < 1e21
        ? literal(String(item), namedNode(`${XSD}integer`))
        : literal(canonicalDouble(item), namedNode(`${XSD}double`))
    }
    throw new Error('Cannot read the value ' + JSON.stringify(item))
  }

  private value(item: Record<string, unknown>, context: Context): Quad_Object {
    const value = item['@value']
    const type = item['@type']
    if (typeof type === 'string') return literal(String(value), namedNode(expandIri(type, context, true)))
    if (typeof item['@language'] === 'string') return literal(String(value), item['@language'])
    if (typeof value === 'number') return this.object(value, undefined, context, defaultGraph())!
    if (typeof value === 'boolean') return literal(String(value), namedNode(`${XSD}boolean`))
    return literal(String(value))
  }

  private list(items: unknown[], definition: TermDefinition | undefined, context: Context,
    graph: Quad_Graph): Quad_Object {
    const itemDefinition = definition && { ...definition, container: undefined }
    const objects = items.flatMap((item) => {
      const object = this.object(item, itemDefinition, context, graph)
      return object ? [object] : []
    })
    let rest: Quad_Object = namedNode(`${RDF}nil`)
    for (const object of objects.reverse()) {
      const cell = blankNode()
      this.quads.push(quad(cell, namedNode(`${RDF}first`), object, graph))
      this.quads.push(quad(cell, namedNode(`${RDF}rest`), rest, graph))
      rest = cell
    }
    return rest
  }

  private subject(id: string, context: Context): Quad_Subject {
    return id.startsWith('_:') ? blankNode(id.slice(2)) : namedNode(expandIri(id, context, false))
  }
}

function emptyContext(): Context {
  return { terms: new Map() }
}

function withContext(active: Context, local: unknown): Context {
  if (local === undefined) return active
  let result: Context = { ...active, terms: new Map(active.terms) }
  for (const item of asArray(local)) {
    if (item === null) {
      result = emptyContext()
    } else if (typeof item === 'string') {
      throw new Error(`Cannot fetch the remote context ${item}: put the context in the document`)
    } else if (isObject(item)) {
      result = defineTerms(result, item)
    }
  }
  return result
}

function defineTerms(active: Context, local: Record<string, unknown>): Context {
  const context: Context = { ...active, terms: new Map(active.terms) }
  if (typeof local['@base'] === 'string') context.base = local['@base']
  if (typeof local['@vocab'] === 'string') context.vocab = expandIri(local['@vocab'], context, true)
  if (local['@vocab'] === null) context.vocab = undefined
  if (typeof local['@language'] === 'string') context.language = local['@language']
  // Terms can refer to each other, in any order: define them until nothing changes
  const pending = Object.entries(local).filter(([term]) => !term.startsWith('@'))
  for (let pass = 0; pass < 3; pass++) {
    for (const [term, definition] of pending) {
      if (definition === null) {
        context.terms.delete(term)
      } else if (typeof definition === 'string') {
        context.terms.set(term, { id: expandIri(definition, context, true) })
      } else if (isObject(definition)) {
        const id = typeof definition['@id'] === 'string' ? definition['@id'] : term
        const type = definition['@type']
        context.terms.set(term, {
          id: expandIri(id, context, true),
          type: typeof type === 'string' && !type.startsWith('@') ? expandIri(type, context, true) : type as string,
          container: typeof definition['@container'] === 'string' ? definition['@container'] : undefined,
          language: definition['@language'] as string | null | undefined,
        })
      }
    }
  }
  return context
}

/**
 * An IRI, compact IRI, term or relative IRI, expanded. Relative to the vocabulary for properties
 * and types, to the base for @id.
 */
function expandIri(value: string, context: Context, vocab: boolean): string {
  if (value.startsWith('@')) return value
  const term = context.terms.get(value)
  if (vocab && term) return term.id
  const colon = value.indexOf(':')
  if (colon > 0) {
    const prefix = value.slice(0, colon)
    const suffix = value.slice(colon + 1)
    if (prefix === '_' || suffix.startsWith('//')) return value
    const definition = context.terms.get(prefix)
    if (definition) return definition.id + suffix
    return value
  }
  if (vocab && context.vocab !== undefined) return context.vocab + value
  if (context.base) {
    try {
      return new URL(value, context.base).toString()
    } catch {
      return value
    }
  }
  return value
}

function isAbsoluteIri(value: string): boolean {
  return /^[A-Za-z][A-Za-z0-9+.-]*:/.test(value) && !value.startsWith('_:')
}

/** As JSON-LD writes doubles: 5.3 becomes 5.3E0 */
function canonicalDouble(value: number): string {
  const [mantissa, exponent] = value.toExponential().split('e')
  return `${mantissa.includes('.') ? mantissa : `${mantissa}.0`}E${Number(exponent)}`
}

function asArray(value: unknown): unknown[] {
  if (value === undefined) return []
  return Array.isArray(value) ? value : [value]
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
