import { Writer } from 'n3'
import { readJsonLd } from '@/ldes/jsonld'

const nTriples = (text: string) => new Writer({ format: 'N-Quads' }).quadsToString(readJsonLd(text).quads)

describe('reading JSON-LD', () => {
  it('expands terms, prefixes and the vocabulary of an inline context', () => {
    const triples = nTriples(JSON.stringify({
      '@context': {
        '@vocab': 'http://example.org/',
        dcterms: 'http://purl.org/dc/terms/',
        xsd: 'http://www.w3.org/2001/XMLSchema#',
        modified: { '@id': 'dcterms:modified', '@type': 'xsd:dateTime' },
        owner: { '@type': '@id' },
      },
      '@id': 'http://example.org/parcel/1',
      '@type': 'Parcel',
      area: 512.4,
      floors: 2,
      modified: '2026-10-26T10:00:00Z',
      owner: 'http://example.org/jane',
    }))

    expect(triples).toContain('<http://example.org/parcel/1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Parcel> .')
    expect(triples).toContain('<http://example.org/area> "5.124E2"^^<http://www.w3.org/2001/XMLSchema#double> .')
    expect(triples).toContain('<http://example.org/floors> "2"^^<http://www.w3.org/2001/XMLSchema#integer> .')
    expect(triples).toContain('<http://purl.org/dc/terms/modified> "2026-10-26T10:00:00Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> .')
    expect(triples).toContain('<http://example.org/owner> <http://example.org/jane> .')
  })

  it('reads value objects, references, nested nodes, languages and lists', () => {
    const { quads, prefixes } = readJsonLd(JSON.stringify({
      '@context': { ex: 'http://example.org/', name: { '@id': 'ex:name', '@language': 'nl' },
        coordinates: { '@id': 'ex:coordinates', '@container': '@list' } },
      '@id': 'ex:a',
      name: 'Vrijdagmarkt',
      'ex:size': { '@value': '625', '@type': 'http://www.w3.org/2001/XMLSchema#integer' },
      'ex:operator': { '@id': 'ex:city' },
      'ex:capacity': { 'ex:total': 625 },
      coordinates: [3.72, 51.05],
      'ex:ignored-because-no-iri': null,
    }))

    expect(prefixes).toEqual({ ex: 'http://example.org/' })
    const text = new Writer({ format: 'N-Quads' }).quadsToString(quads)
    expect(text).toContain('<http://example.org/name> "Vrijdagmarkt"@nl')
    expect(text).toContain('<http://example.org/size> "625"^^<http://www.w3.org/2001/XMLSchema#integer>')
    expect(text).toContain('<http://example.org/operator> <http://example.org/city>')
    expect(text).toMatch(/<http:\/\/example.org\/capacity> _:\S+ \./)
    expect(text).toContain('<http://www.w3.org/1999/02/22-rdf-syntax-ns#first> "3.72E0"')
    expect(quads.filter((q) => q.predicate.value.endsWith('#rest'))).toHaveLength(2)
  })

  it('reads the nodes of a graph, and named graphs', () => {
    const text = nTriples(JSON.stringify({
      '@context': { ex: 'http://example.org/' },
      '@graph': [
        { '@id': 'ex:a', 'ex:p': 'one' },
        { '@id': 'ex:g', '@graph': [{ '@id': 'ex:b', 'ex:p': 'two' }] },
      ],
    }))

    expect(text).toContain('<http://example.org/a> <http://example.org/p> "one" .')
    expect(text).toContain('<http://example.org/b> <http://example.org/p> "two" <http://example.org/g> .')
  })
})
