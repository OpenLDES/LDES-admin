import type { EventStream } from '@/domain/ldes'
import { prefixLines } from '@/domain/rdf'
import { exampleFor, examples } from '@/examples'

/**
 * Realistic members for each kind of sample collection: parcels, addresses, mobility hindrances
 * and water quality observations, and those of the examples (sensor observations, parking
 * occupancy), as version objects in Turtle.
 */
const municipalities = ['Leuven', 'Gent', 'Antwerpen', 'Brugge', 'Hasselt', 'Mechelen']
const streets = ['Bondgenotenlaan', 'Veldstraat', 'Meir', 'Steenstraat', 'Demerstraat', 'Bruul']

/**
 * A member of a collection as it would appear on a page, in Turtle.
 */
export function sampleMember(collection: string, index: number, timestamp: Date):
  { id: string; isVersionOf: string; content: string } {
  const example = examples.find((candidate) => candidate.collection === collection)
  if (example) return example.member(index, timestamp)
  const time = timestamp.toISOString().replace('.000', '')
  const n = 1000 + index * 37
  const municipality = municipalities[index % municipalities.length]
  const lon = (4.7 + (index % 7) * 0.0123).toFixed(4)
  const lat = (50.87 + (index % 5) * 0.0087).toFixed(4)
  let state: string
  let body: string
  let prefixes: string[]
  if (collection.includes('address')) {
    state = `https://data.vlaanderen.be/id/adres/${n * 13}`
    prefixes = ['adres', 'dcterms', 'prov', 'xsd']
    body = `    a adres:Adres ;
    adres:huisnummer "${(index % 120) + 1}" ;
    adres:gemeentenaam "${municipality}" ;
    adres:straatnaam "${streets[index % streets.length]}" ;`
  } else if (collection.includes('hindrance')) {
    state = `https://data.vlaanderen.be/id/hinder/GIPOD-${1_287_000 + n}`
    prefixes = ['mobiliteit', 'dcterms', 'prov', 'xsd', 'geo']
    body = `    a mobiliteit:Mobiliteitshinder ;
    mobiliteit:beschrijving "Road works in ${municipality}" ;
    mobiliteit:status "${index % 3 === 0 ? 'planned' : 'ongoing'}" ;
    geo:asWKT "POINT (${lon} ${lat})"^^geo:wktLiteral ;`
  } else if (collection.includes('water')) {
    state = `https://data.vlaanderen.example/id/observation/station-${(index % 9) + 1}/ph`
    prefixes = ['sosa', 'dcterms', 'prov', 'xsd']
    body = `    a sosa:Observation ;
    sosa:observedProperty <https://data.vlaanderen.example/id/property/ph> ;
    sosa:hasSimpleResult ${(7 + (index % 10) / 10).toFixed(1)} ;
    sosa:resultTime "${time}"^^xsd:dateTime ;`
  } else {
    const capakey = `24${String(62 + (index % 30)).padStart(3, '0')}B${String(n).padStart(4, '0')}/00A000`
    state = `https://data.vlaanderen.be/id/perceel/${capakey.replace('/', '-')}`
    prefixes = ['perceel', 'dcterms', 'prov', 'xsd', 'geo']
    body = `    a perceel:Perceel ;
    perceel:capakey "${capakey}" ;
    perceel:municipality "${municipality}" ;
    geo:asWKT "POLYGON ((${lon} ${lat}, ${lon} ${(Number(lat) + 0.0005).toFixed(4)}, ${(Number(lon) + 0.0007).toFixed(4)} ${lat}, ${lon} ${lat}))"^^geo:wktLiteral ;`
  }
  const id = `${state}/${time}`
  return {
    id,
    isVersionOf: state,
    content: `${prefixLines(...prefixes)}

<${id}>
${body}
    dcterms:isVersionOf <${state}> ;
    prov:generatedAtTime "${time}"^^xsd:dateTime .`,
  }
}

/**
 * A member to start from when ingesting directly on an event stream: one of the example it fits,
 * else one after the name of its collection
 */
export function memberTemplate(stream: EventStream | string, now = new Date()): string {
  const index = Math.floor(now.getTime() / 60_000) % 997
  const example = typeof stream === 'string' ? undefined : exampleFor(stream)
  if (example) return example.member(index, now).content
  return sampleMember(typeof stream === 'string' ? stream : stream.collection, index, now).content
}
