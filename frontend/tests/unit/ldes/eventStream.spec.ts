import { eventStreamProperties, kafkaSourceProperties, retentionPolicyKinds } from '@/ldes/catalog'
import type { DefinitionProblem } from '@/ldes/definition'
import {
  addEventSourcePolicy,
  collectionOf,
  eventSourceOf,
  eventSourcePolicies,
  eventStreamProblems,
  eventStreamTemplate,
  kafkaSourceOf,
  removeEventSourcePolicy,
  renameCollection,
  writeKafkaValue,
  writeStreamValue,
} from '@/ldes/eventStream'
import { parseTurtle, type RdfGraph, writeTurtle } from '@/ldes/turtle'

const template = () => eventStreamTemplate('http://localhost:8080', 'parcels')
const property = (key: string) => eventStreamProperties.find((p) => p.key === key)!
const messages = (problems: DefinitionProblem[]) => problems.map((problem) => `${problem.key ?? ''}: ${problem.message}`)

function parsed(body: string): RdfGraph {
  const result = parseTurtle(`@prefix ldes: <https://w3id.org/ldes#> . @prefix tree: <https://w3id.org/tree#> .
    @prefix sh: <http://www.w3.org/ns/shacl#> . @prefix dcterms: <http://purl.org/dc/terms/> .
    @prefix server: <http://localhost:8080/> . ${body}`)
  if (!result.ok) throw new Error(result.error)
  return result.graph
}

describe('event stream definitions', () => {
  it('starts from a definition the server accepts', () => {
    expect(eventStreamProblems(template())).toEqual([])
    expect(writeTurtle(template())).toContain(`server:parcels a ldes:EventStream ;
    ldes:timestampPath dcterms:created ;
    ldes:versionOfPath dcterms:isVersionOf ;
    tree:shape [ a sh:NodeShape ] .`)
  })

  it('renames the collection, to a name the server keeps', () => {
    const graph = renameCollection(template(), 'trees')

    expect(collectionOf(graph)).toBe('trees')
    expect(writeTurtle(graph)).toContain('server:trees a ldes:EventStream')
    expect(messages(eventStreamProblems(renameCollection(graph, '2026-trees'))))
      .toEqual(['collection: Start with a letter: the server would drop leading digits'])
  })

  it('takes a prefixed name from the form, and declares a well-known prefix', () => {
    const graph = writeStreamValue(template(), property('ldes:timestampPath'), 'prov:generatedAtTime')

    expect(writeTurtle(graph)).toContain('@prefix prov:    <http://www.w3.org/ns/prov#> .')
    expect(writeTurtle(graph)).toContain('ldes:timestampPath prov:generatedAtTime ;')
    expect(messages(eventStreamProblems(writeStreamValue(graph, property('ldes:timestampPath'), 'my:time'))))
      .toEqual(['ldes:timestampPath: Unknown prefix my: declare it in the definition, or enter the full IRI'])
  })

  it('only allows a version delimiter when the server creates versions', () => {
    const delimiter = property('ldes:versionDelimiter')
    const graph = writeStreamValue(template(), delimiter, '#')
    expect(messages(eventStreamProblems(graph)))
      .toEqual(['ldes:versionDelimiter: Only allowed when ldes:createVersions is true'])

    const versioned = writeStreamValue(graph, property('ldes:createVersions'), true)
    expect(eventStreamProblems(versioned)).toEqual([])
    expect(writeTurtle(versioned)).toContain(`ldes:createVersions true ;
    ldes:versionDelimiter "#" ;
    tree:shape`)
  })

  it('reports what the shapes of the admin API reject', () => {
    const graph = parsed(`server:parcels a ldes:EventStream ; ldes:timestampPath "created" ;
      ldes:createVersions "true" ; ldes:skolemizationDomain "https://example.org" ; ldes:size 3 .
      <http://example.org/x> dcterms:title "Loose" .`)

    expect(messages(eventStreamProblems(graph))).toEqual([
      'ldes:timestampPath: Must be an IRI, not the literal "created"',
      'ldes:versionOfPath: Required',
      'ldes:createVersions: Must be typed xsd:boolean, not xsd:string',
      'ldes:skolemizationDomain: Must be an IRI, not the literal "https://example.org"',
      'ldes:size: Not a property of an event stream; the server ignores it',
      'tree:shape: Needs a tree:shape, a sh:NodeShape the members must conform to',
      ': http://example.org/x is not part of the event stream; the server rejects statements about it',
    ])
  })

  it('adds and removes retention policies of the event source', () => {
    let graph = addEventSourcePolicy(template(), retentionPolicyKinds[1])
    expect(writeTurtle(graph)).toContain(`ldes:eventSource [
        a ldes:EventSource ;
        ldes:retentionPolicy [
            a ldes:LatestVersionSubset ;
            ldes:amount 1
        ]
    ] ;`)
    expect(eventStreamProblems(graph)).toEqual([])

    graph = removeEventSourcePolicy(graph, eventSourcePolicies(graph)[0])
    expect(eventSourceOf(graph)).toBeUndefined()
    expect(writeTurtle(graph)).toBe(writeTurtle(template()))
  })

  it('refuses retention policies the server does not know', () => {
    const graph = parsed(`server:parcels a ldes:EventStream ; ldes:timestampPath dcterms:created ;
      ldes:versionOfPath dcterms:isVersionOf ; tree:shape [ a sh:NodeShape ] ;
      ldes:eventSource [ a ldes:EventSource ; ldes:retentionPolicy [ a ldes:FooPolicy ] ] .`)

    expect(messages(eventStreamProblems(graph)))
      .toEqual([': Not a retention policy the server knows: use ldes:DurationAgoPolicy or ldes:LatestVersionSubset'])
  })

  it('creates a Kafka source with its first property and removes it with its last', () => {
    const [topic, mimeType] = kafkaSourceProperties
    let graph = writeKafkaValue(template(), topic, 'parcels')

    expect(writeTurtle(graph)).toContain('ldes:kafkaSource [ ldes:topic "parcels" ] ;')
    expect(messages(eventStreamProblems(graph))).toEqual(['ldes:mimeType: Required'])

    graph = writeKafkaValue(graph, mimeType, 'application/n-quads')
    expect(eventStreamProblems(graph)).toEqual([])

    graph = writeKafkaValue(writeKafkaValue(graph, topic, ''), mimeType, undefined)
    expect(kafkaSourceOf(graph)).toBeUndefined()
  })
})
