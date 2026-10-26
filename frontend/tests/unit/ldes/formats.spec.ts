import { Parser } from 'n3'
import { sensors } from '@/examples/sensors'
import { convertRdf, convertTurtle, isRdfFormat } from '@/ldes/formats'

const NOW = new Date('2026-10-25T13:20:00Z')
const member = sensors.member(0, NOW)
const quadCount = (text: string, format: string) => new Parser({ format }).parse(text).length

describe('converting a member to another format', () => {
  it('leaves Turtle as it is', () => {
    expect(convertTurtle(member.content, 'text/turtle')).toBe(member.content)
  })

  it('writes N-Triples and N-Quads with the same statements', () => {
    const triples = convertTurtle(member.content, 'application/n-triples')
    const quads = convertTurtle(member.content, 'application/n-quads')

    expect(triples).toContain(`<${member.id}> <http://purl.org/dc/terms/isVersionOf> <${member.isVersionOf}> .`)
    expect(quadCount(triples, 'N-Triples')).toBe(quadCount(member.content, 'Turtle'))
    expect(quadCount(quads, 'N-Quads')).toBe(quadCount(member.content, 'Turtle'))
  })

  it('writes JSON-LD with the prefixes as context and the blank nodes nested', () => {
    const jsonLd = JSON.parse(convertTurtle(member.content, 'application/ld+json'))

    expect(jsonLd['@context']).toMatchObject({ sosa: 'http://www.w3.org/ns/sosa/', unit: 'http://qudt.org/vocab/unit/' })
    expect(jsonLd).toMatchObject({
      '@id': member.id,
      '@type': 'sosa:Observation',
      'dcterms:isVersionOf': { '@id': member.isVersionOf },
      'sosa:resultTime': { '@value': '2026-10-25T13:20:00Z', '@type': 'xsd:dateTime' },
      'sosa:hasResult': { '@type': 'qudt:QuantityValue', 'qudt:unit': { '@id': 'unit:DEG_C' } },
    })
    expect(jsonLd['sosa:hasResult']['@id']).toBeUndefined()
  })

  it('writes several subjects as a graph', () => {
    const turtle = '@prefix ex: <http://example.org/> . ex:a ex:p "one" . ex:b ex:p "two"@en .'

    expect(JSON.parse(convertTurtle(turtle, 'application/ld+json'))).toEqual({
      '@context': { ex: 'http://example.org/' },
      '@graph': [
        { '@id': 'ex:a', 'ex:p': 'one' },
        { '@id': 'ex:b', 'ex:p': { '@value': 'two', '@language': 'en' } },
      ],
    })
  })
})

describe('converting linked data between formats', () => {
  const sameStatements = (a: string, formatA: string, b: string, formatB: string) => {
    const canonical = (text: string, format: string) => new Parser({ format }).parse(text)
      .map((q) => [q.subject, q.predicate, q.object]
        .map((term) => (term.termType === 'BlankNode' ? '_' : term.id)).join(' '))
      .sort()
    expect(canonical(a, formatA)).toEqual(canonical(b, formatB))
  }

  it('reads the JSON-LD it writes back into the same statements', () => {
    const jsonLd = convertRdf(member.content, 'text/turtle', 'application/ld+json')

    const turtle = convertRdf(jsonLd, 'application/ld+json', 'text/turtle')

    sameStatements(turtle, 'Turtle', member.content, 'Turtle')
    expect(turtle).toContain('@prefix sosa:')
    expect(turtle).toContain('a sosa:Observation')
  })

  it('writes N-Quads as Turtle', () => {
    const quads = convertRdf(member.content, 'text/turtle', 'application/n-quads')

    sameStatements(convertRdf(quads, 'application/n-quads', 'text/turtle'), 'Turtle', member.content, 'Turtle')
  })

  it('explains what cannot be converted', () => {
    expect(() => convertRdf('<http://example.org/a> <http://example.org/b> "c" <http://example.org/graph> .', 'application/n-quads', 'text/turtle'))
      .toThrow('Turtle cannot hold named graphs: choose N-Quads or JSON-LD')
    expect(() => convertRdf('{"@context": "https://schema.org/", "name": "x"}', 'application/ld+json', 'text/turtle'))
      .toThrow('Cannot fetch the remote context https://schema.org/: put the context in the document')
    expect(() => convertRdf('<a> <b> .', 'text/turtle', 'application/ld+json')).toThrow()
  })

  it('knows which content types are RDF formats', () => {
    expect(isRdfFormat('application/ld+json; charset=utf-8')).toBe(true)
    expect(isRdfFormat('application/json')).toBe(false)
    expect(isRdfFormat('text/csv')).toBe(false)
  })
})
