import { Parser, Writer } from 'n3'
import type { Literal, Quad, Term } from 'n3'
import type { RdfFormat } from '@/domain/ldes'
import { PREFIXES } from '@/domain/rdf'
import { readJsonLd } from './jsonld'
import { writeTurtle } from './turtle'

const RDF_FORMATS: RdfFormat[] = ['text/turtle', 'application/ld+json', 'application/n-quads',
  'application/n-triples']

/** Whether a content type is one of the RDF formats that can be converted, ignoring parameters */
export function isRdfFormat(contentType: string): contentType is RdfFormat {
  return RDF_FORMATS.includes(contentType.split(';')[0].trim().toLowerCase() as RdfFormat)
}

/**
 * Writes Turtle in another RDF format, e.g. to offer a member in the format chosen for ingesting.
 * N3.js writes N-Triples and N-Quads; JSON-LD is written here, compacted with the prefixes of the
 * Turtle, blank nodes used once nested in their subject.
 */
export function convertTurtle(turtle: string, format: RdfFormat): string {
  return convertRdf(turtle, 'text/turtle', format)
}

/**
 * Linked data in another RDF format. Turtle is written as by hand, with the prefixes of the
 * source; JSON-LD is read without fetching remote contexts (ldes/jsonld.ts).
 *
 * @throws Error when the content is not valid in its format, or cannot be written in the other
 */
export function convertRdf(content: string, from: RdfFormat, to: RdfFormat): string {
  if (from === to) return content
  const { quads, prefixes } = parseRdf(content, from)
  if (to === 'application/ld+json') return JSON.stringify(toJsonLd(quads, prefixes), null, 2)
  if (to === 'application/n-quads' || to === 'application/n-triples') {
    if (to === 'application/n-triples' && quads.some((q) => q.graph.termType !== 'DefaultGraph')) {
      throw new Error('N-Triples cannot hold named graphs: choose N-Quads')
    }
    return new Writer({ format: to === 'application/n-quads' ? 'N-Quads' : 'N-Triples' }).quadsToString(quads)
  }
  if (quads.some((q) => q.graph.termType !== 'DefaultGraph')) {
    throw new Error('Turtle cannot hold named graphs: choose N-Quads or JSON-LD')
  }
  return writeTurtle({ prefixes, quads })
}

function parseRdf(content: string, format: RdfFormat): { quads: Quad[]; prefixes: Record<string, string> } {
  if (format === 'application/ld+json') return readJsonLd(content)
  const prefixes: Record<string, string> = {}
  const quads = new Parser({ format: format === 'text/turtle' ? 'Turtle' : format === 'application/n-quads' ? 'N-Quads' : 'N-Triples' })
    .parse(content, null, (prefix, namespace) => { prefixes[prefix] = namespace.value })
  return { quads, prefixes }
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
