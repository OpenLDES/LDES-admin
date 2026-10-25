import { DataFactory, Parser } from 'n3'
import type { BlankNode, Literal, NamedNode, Quad, Quad_Object, Quad_Subject, Term } from 'n3'
import { PREFIXES } from '@/domain/rdf'

/**
 * An RDF graph as written in Turtle, with the prefixes it declares: the definition of an event
 * stream or a view. The forms change the graph and write it out again; the Turtle editor parses
 * it. Functions return a new graph, so Vue sees every change.
 */
export interface RdfGraph {
  prefixes: Record<string, string>
  quads: Quad[]
}

export type ParsedTurtle = { ok: true; graph: RdfGraph } | { ok: false; error: string }

/** Something the graph describes: the event stream, a view, a fragmentation… */
export type Resource = NamedNode | BlankNode

const { namedNode, quad, blankNode } = DataFactory

export const RDF_TYPE = `${PREFIXES.rdf}type`
const RDF_FIRST = `${PREFIXES.rdf}first`
const RDF_REST = `${PREFIXES.rdf}rest`
const RDF_NIL = `${PREFIXES.rdf}nil`
const XSD = PREFIXES.xsd

export function parseTurtle(text: string): ParsedTurtle {
  const prefixes: Record<string, string> = {}
  try {
    const quads = new Parser({ format: 'Turtle' })
      .parse(text, null, (prefix, namespace) => { prefixes[prefix] = namespace.value })
    return { ok: true, graph: { prefixes, quads } }
  } catch (e) {
    return { ok: false, error: (e instanceof Error ? e.message : String(e)).split('\n')[0] }
  }
}

// Reading -------------------------------------------------------------------------------------

export function objectsOf(graph: RdfGraph, subject: Term, predicate: string): Quad_Object[] {
  return graph.quads
    .filter((q) => q.subject.equals(subject) && q.predicate.value === predicate)
    .map((q) => q.object)
}

export function objectOf(graph: RdfGraph, subject: Term, predicate: string): Quad_Object | undefined {
  return objectsOf(graph, subject, predicate)[0]
}

/** Subjects with the predicate, in document order; with an object, only those with that object */
export function subjectsWith(graph: RdfGraph, predicate: string, object?: string): Quad_Subject[] {
  const subjects: Quad_Subject[] = []
  for (const q of graph.quads) {
    if (q.predicate.value === predicate && (object === undefined || q.object.value === object)
      && !subjects.some((subject) => subject.equals(q.subject))) {
      subjects.push(q.subject)
    }
  }
  return subjects
}

export function predicatesOf(graph: RdfGraph, subject: Term): string[] {
  return [...new Set(graph.quads.filter((q) => q.subject.equals(subject)).map((q) => q.predicate.value))]
}

/** The items of an RDF list, e.g. ( [ ... ] [ ... ] ); undefined when it is not a proper list */
export function listItems(graph: RdfGraph, head: Term): Quad_Object[] | undefined {
  const items: Quad_Object[] = []
  const visited = new Set<string>()
  let cell: Term = head
  while (!(cell.termType === 'NamedNode' && cell.value === RDF_NIL)) {
    if (cell.termType !== 'BlankNode' || visited.has(cell.value)) return undefined
    visited.add(cell.value)
    const first = objectsOf(graph, cell, RDF_FIRST)
    const rest = objectsOf(graph, cell, RDF_REST)
    if (first.length !== 1 || rest.length !== 1) return undefined
    items.push(first[0])
    cell = rest[0]
  }
  return items
}

export function isResource(term: Term): term is Resource {
  return term.termType === 'NamedNode' || term.termType === 'BlankNode'
}

// Changing ------------------------------------------------------------------------------------

/**
 * Replaces the objects of a predicate. A predicate that is already there keeps its place; a new
 * one goes before the first predicate that comes after it in the given order, or else last. Blank
 * nodes that nothing refers to any longer are removed with everything they hold.
 */
export function setObjects(graph: RdfGraph, subject: Quad_Subject, predicate: string, objects: Quad_Object[],
  order: string[] = []): RdfGraph {
  const isTarget = (q: Quad) => q.subject.equals(subject) && q.predicate.value === predicate
  const first = graph.quads.findIndex(isTarget)
  const previous = graph.quads.filter(isTarget).map((q) => q.object)
  const remaining = graph.quads.filter((q) => !isTarget(q))
  const at = first >= 0
    ? graph.quads.slice(0, first).filter((q) => !isTarget(q)).length
    : insertionIndex(remaining, subject, predicate, order)
  const added = objects.map((object) => quad(subject, namedNode(predicate), object))
  const quads = [...remaining.slice(0, at), ...added, ...remaining.slice(at)]
  return { ...graph, quads: withoutOrphans(quads, previous) }
}

/** Replaces the objects of a predicate by an RDF list of the items */
export function setList(graph: RdfGraph, subject: Quad_Subject, predicate: string, items: Quad_Object[],
  order: string[] = []): RdfGraph {
  const cells = items.map(() => blankNode())
  const list = items.flatMap((item, i) => [
    quad(cells[i], namedNode(RDF_FIRST), item),
    quad(cells[i], namedNode(RDF_REST), cells[i + 1] ?? namedNode(RDF_NIL)),
  ])
  const head: Quad_Object = cells[0] ?? namedNode(RDF_NIL)
  return setObjects({ ...graph, quads: [...graph.quads, ...list] }, subject, predicate, [head], order)
}

/** Adds a blank node with the given triples, e.g. a fragmentation; refer to it with setObjects */
export function addResource(graph: RdfGraph, properties: [string, Quad_Object][]):
  { graph: RdfGraph; node: BlankNode } {
  const node = blankNode()
  const added = properties.map(([predicate, object]) => quad(node, namedNode(predicate), object))
  return { graph: { ...graph, quads: [...graph.quads, ...added] }, node }
}

/** Gives a resource another IRI, wherever it occurs */
export function renameResource(graph: RdfGraph, from: NamedNode, to: NamedNode): RdfGraph {
  return {
    ...graph,
    quads: graph.quads.map((q) => (q.subject.equals(from) || q.object.equals(from)
      ? quad(q.subject.equals(from) ? to : q.subject, q.predicate, q.object.equals(from) ? to : q.object)
      : q)),
  }
}

export function withPrefix(graph: RdfGraph, prefix: string, namespace: string): RdfGraph {
  return graph.prefixes[prefix] === namespace ? graph
    : { ...graph, prefixes: { ...graph.prefixes, [prefix]: namespace } }
}

function insertionIndex(quads: Quad[], subject: Term, predicate: string, order: string[]): number {
  const own = quads.flatMap((q, i) => (q.subject.equals(subject) ? [i] : []))
  if (own.length === 0) return quads.length
  const rank = order.indexOf(predicate)
  if (rank >= 0) {
    const later = own.find((i) => order.indexOf(quads[i].predicate.value) > rank)
    if (later !== undefined) return later
  }
  return own[own.length - 1] + 1
}

/** Removes the blank nodes among the candidates that are no longer referred to, recursively */
function withoutOrphans(quads: Quad[], candidates: Term[]): Quad[] {
  let result = quads
  const queue = candidates.filter((term) => term.termType === 'BlankNode')
  while (queue.length > 0) {
    const node = queue.shift()!
    if (result.some((q) => q.object.equals(node))) continue
    const own = result.filter((q) => q.subject.equals(node))
    result = result.filter((q) => !q.subject.equals(node))
    queue.push(...own.map((q) => q.object).filter((term) => term.termType === 'BlankNode'))
  }
  return result
}

// Terms as text -------------------------------------------------------------------------------

const LOCAL_NAME = /^([A-Za-z0-9_]([\w.-]*[\w-])?)?$/

/** An IRI as a prefixed name when one of the prefixes fits, e.g. tree:pageSize; else undefined */
export function prefixedName(graph: RdfGraph, iri: string): string | undefined {
  const [prefix, namespace] = Object.entries(graph.prefixes)
    .filter(([, candidate]) => iri.startsWith(candidate) && LOCAL_NAME.test(iri.slice(candidate.length)))
    .sort(([, a], [, b]) => b.length - a.length)[0] ?? []
  return prefix === undefined ? undefined : `${prefix}:${iri.slice(namespace!.length)}`
}

/** A term as a form shows it: an IRI prefixed where possible, the value of a literal */
export function termText(graph: RdfGraph, term: Term): string {
  if (term.termType === 'NamedNode') return prefixedName(graph, term.value) ?? term.value
  if (term.termType === 'Literal') return term.value
  return ''
}

/**
 * The IRI for what is entered in a form: a prefixed name with a declared prefix, or with one of the
 * well-known prefixes, or else the text itself, without angle brackets. A well-known prefix that
 * fits gets declared, so the Turtle stays readable.
 */
export function iriFromText(graph: RdfGraph, text: string): { graph: RdfGraph; iri: string } {
  const value = text.trim().replace(/^<(.*)>$/, '$1')
  const match = /^([A-Za-z][\w.-]*)?:(.*)$/.exec(value)
  const prefix = match && !value.includes('//') ? match[1] ?? '' : undefined
  if (prefix !== undefined && graph.prefixes[prefix] !== undefined) {
    return { graph, iri: graph.prefixes[prefix] + match![2] }
  }
  const iri = prefix !== undefined && PREFIXES[prefix] ? PREFIXES[prefix] + match![2] : value
  return { graph: declarePrefixFor(graph, iri), iri }
}

/** Declares the well-known prefix that fits the IRI, unless a declared one does already */
export function declarePrefixFor(graph: RdfGraph, iri: string): RdfGraph {
  if (prefixedName(graph, iri)) return graph
  const known = Object.entries(PREFIXES).find(([name, namespace]) => iri.startsWith(namespace)
    && LOCAL_NAME.test(iri.slice(namespace.length)) && graph.prefixes[name] === undefined)
  return known ? withPrefix(graph, known[0], known[1]) : graph
}

// Writing -------------------------------------------------------------------------------------

const INDENT = '    '

/**
 * The graph as Turtle that reads like a hand-written definition: blank nodes used once are nested
 * in [ ], lists written as ( ), and rdf:type comes first, as a.
 */
export function writeTurtle(graph: RdfGraph): string {
  const prefixes = Object.entries(graph.prefixes)
  const width = Math.max(0, ...prefixes.map(([prefix]) => prefix.length + 1))
  const head = prefixes.map(([prefix, namespace]) => `@prefix ${`${prefix}:`.padEnd(width)} <${namespace}> .`)
  const body = new TurtleWriter(graph).statements()
  return `${[head.join('\n'), body].filter(Boolean).join('\n\n')}\n`
}

class TurtleWriter {
  private readonly references = new Map<string, number>()
  private readonly written = new Set<string>()
  private readonly labels = new Map<string, string>()

  constructor(private readonly graph: RdfGraph) {
    for (const q of graph.quads) {
      if (q.object.termType === 'BlankNode') {
        this.references.set(q.object.value, (this.references.get(q.object.value) ?? 0) + 1)
      }
    }
  }

  statements(): string {
    const subjects = this.graph.quads.map((q) => q.subject)
      .filter((subject, i, all) => all.findIndex((other) => other.equals(subject)) === i)
    const blocks: string[] = []
    for (const subject of subjects) {
      if (!this.isWritten(subject) && !this.isNested(subject)) blocks.push(this.statement(subject))
    }
    // Blank nodes that only refer to each other have no place to nest in
    for (const subject of subjects) {
      if (!this.isWritten(subject)) blocks.push(this.statement(subject))
    }
    return blocks.join('\n\n')
  }

  private statement(subject: Term): string {
    this.written.add(key(subject))
    const name = subject.termType === 'BlankNode'
      ? (this.references.has(subject.value) ? this.label(subject) : '[]')
      : this.term(subject)
    const groups = this.groups(subject)
    const lines = groups.map(([predicate, objects], i) =>
      `${i === 0 ? '' : INDENT}${predicate} ${this.objects(objects, i === 0 ? '' : INDENT)}`)
    return `${name} ${lines.join(' ;\n')} .`
  }

  /** The predicates of a subject with their objects, in document order, rdf:type first */
  private groups(subject: Term): [string, Term[]][] {
    const groups = new Map<string, Term[]>()
    for (const q of this.graph.quads) {
      if (!q.subject.equals(subject)) continue
      groups.set(q.predicate.value, [...(groups.get(q.predicate.value) ?? []), q.object])
    }
    return [...groups].sort(([a], [b]) => Number(b === RDF_TYPE) - Number(a === RDF_TYPE))
      .map(([predicate, objects]) => [predicate === RDF_TYPE ? 'a' : this.iri(predicate), objects])
  }

  private objects(objects: Term[], indent: string): string {
    return objects.map((object) => this.object(object, indent)).join(', ')
  }

  /** An object on a line with the given indentation */
  private object(term: Term, indent: string): string {
    if (term.termType === 'NamedNode' && term.value === RDF_NIL) return '()'
    if (term.termType !== 'BlankNode' || !this.isNested(term) || this.isWritten(term)) return this.term(term)
    const items = listItems(this.graph, term)
    if (items && this.isPlainList(term)) {
      this.markList(term)
      return `( ${items.map((item) => this.object(item, indent)).join(' ')} )`
    }
    this.written.add(key(term))
    const groups = this.groups(term)
    if (groups.length === 0) return '[]'
    const [[predicate, objects]] = groups
    if (groups.length === 1 && objects.length === 1 && this.isSimple(objects[0])) {
      return `[ ${predicate} ${this.term(objects[0])} ]`
    }
    const inner = indent + INDENT
    const lines = groups.map(([p, o]) => `${inner}${p} ${this.objects(o, inner)}`)
    return `[\n${lines.join(' ;\n')}\n${indent}]`
  }

  /** A list whose cells hold nothing but rdf:first and rdf:rest, and are not shared */
  private isPlainList(head: Term): boolean {
    let cell: Term = head
    while (cell.termType === 'BlankNode') {
      if (!this.isNested(cell) || predicatesOf(this.graph, cell).length !== 2) return false
      cell = objectOf(this.graph, cell, RDF_REST)!
    }
    return true
  }

  private markList(head: Term) {
    let cell: Term = head
    while (cell.termType === 'BlankNode') {
      this.written.add(key(cell))
      cell = objectOf(this.graph, cell, RDF_REST)!
    }
  }

  private isNested(term: Term): boolean {
    return term.termType === 'BlankNode' && this.references.get(term.value) === 1
  }

  private isWritten(term: Term): boolean {
    return this.written.has(key(term))
  }

  private isSimple(term: Term): boolean {
    return term.termType !== 'BlankNode' || !this.isNested(term) || this.isWritten(term)
  }

  private term(term: Term): string {
    switch (term.termType) {
      case 'NamedNode':
        return term.value === RDF_NIL ? '()' : this.iri(term.value)
      case 'BlankNode':
        return this.label(term)
      case 'Literal':
        return this.literal(term as Literal)
      default:
        return term.value
    }
  }

  private iri(iri: string): string {
    return prefixedName(this.graph, iri) ?? `<${iri}>`
  }

  private literal(literal: Literal): string {
    const value = literal.value
    const datatype = literal.datatype.value
    if (literal.language) return `${quoted(value)}@${literal.language}`
    if (datatype === `${XSD}string`) return quoted(value)
    if (datatype === `${XSD}integer` && /^[+-]?\d+$/.test(value)) return value
    if (datatype === `${XSD}decimal` && /^[+-]?\d*\.\d+$/.test(value)) return value
    if (datatype === `${XSD}boolean` && /^(true|false)$/.test(value)) return value
    return `${quoted(value)}^^${this.iri(datatype)}`
  }

  private label(node: Term): string {
    if (!this.labels.has(node.value)) this.labels.set(node.value, `_:b${this.labels.size}`)
    return this.labels.get(node.value)!
  }
}

function key(term: Term): string {
  return `${term.termType}:${term.value}`
}

function quoted(value: string): string {
  const escaped = value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
    .replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t')
  return `"${escaped}"`
}
