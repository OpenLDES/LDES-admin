import { Parser, Writer } from 'n3'
import type { Literal, Quad, Term } from 'n3'
import type { RdfFormat } from '@/domain/ldes'
import { PREFIXES } from '@/domain/rdf'

/**
 * Writes Turtle in another RDF format, e.g. to offer a member in the format chosen for ingesting.
 * N3.js writes N-Triples and N-Quads; JSON-LD is written here, compacted with the prefixes of the
 * Turtle, blank nodes used once nested in their subject.
 */
export function convertTurtle(turtle: string, format: RdfFormat): string {
  if (format === 'text/turtle') return turtle
  const prefixes: Record<string, string> = {}
  const quads = new Parser({ format: 'Turtle' })
    .parse(turtle, null, (prefix, namespace) => { prefixes[prefix] = namespace.value })
  if (format === 'application/ld+json') return JSON.stringify(toJsonLd(quads, prefixes), null, 2)
  return new Writer({ format: format === 'application/n-quads' ? 'N-Quads' : 'N-Triples' })
    .quadsToString(quads)
}

type JsonLdNode = Record<string, unknown>

function toJsonLd(quads: Quad[], prefixes: Record<string, string>): JsonLdNode {
  const references = new Map<string, number>()
  quads.filter((q) => q.object.termType === 'BlankNode')
    .forEach((q) => references.set(q.object.value, (references.get(q.object.value) ?? 0) + 1))
  const nested = (term: Term) => term.termType === 'BlankNode' && references.get(term.value) === 1
  const compact = (iri: string) => {
    const [prefix, namespace] = Object.entries(prefixes)
      .filter(([, candidate]) => iri.startsWith(candidate)).sort(([, a], [, b]) => b.length - a.length)[0] ?? []
    return prefix === undefined ? iri : `${prefix}:${iri.slice(namespace!.length)}`
  }

  const node = (subject: Term): JsonLdNode => {
    const result: JsonLdNode = nested(subject) ? {} : { '@id': id(subject) }
    for (const q of quads.filter((candidate) => candidate.subject.equals(subject))) {
      const key = q.predicate.value === `${PREFIXES.rdf}type` ? '@type' : compact(q.predicate.value)
      const item = key === '@type' ? compact(q.object.value) : value(q.object)
      const previous = result[key]
      result[key] = previous === undefined ? item : [...(Array.isArray(previous) ? previous : [previous]), item]
    }
    return result
  }
  const id = (term: Term) => (term.termType === 'BlankNode' ? `_:${term.value}` : compact(term.value))
  const value = (term: Term): unknown => {
    if (term.termType === 'Literal') {
      const literal = term as Literal
      if (literal.language) return { '@value': literal.value, '@language': literal.language }
      if (literal.datatype.value === `${PREFIXES.xsd}string`) return literal.value
      return { '@value': literal.value, '@type': compact(literal.datatype.value) }
    }
    return nested(term) ? node(term) : { '@id': id(term) }
  }

  const subjects = quads.map((q) => q.subject)
    .filter((subject, i, all) => all.findIndex((other) => other.equals(subject)) === i && !nested(subject))
  const context = { '@context': prefixes }
  return subjects.length === 1
    ? { ...context, ...node(subjects[0]) }
    : { ...context, '@graph': subjects.map(node) }
}
