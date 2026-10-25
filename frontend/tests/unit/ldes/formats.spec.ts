import { Parser } from 'n3'
import { sensors } from '@/examples/sensors'
import { convertTurtle } from '@/ldes/formats'

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
