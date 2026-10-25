import { DataFactory } from 'n3'
import {
  iriFromText,
  listItems,
  objectOf,
  parseTurtle,
  type RdfGraph,
  setList,
  setObjects,
  writeTurtle,
} from '@/ldes/turtle'

const { namedNode } = DataFactory
const TREE = 'https://w3id.org/tree#'

function parsed(text: string): RdfGraph {
  const result = parseTurtle(text)
  if (!result.ok) throw new Error(result.error)
  return result.graph
}

const VIEW = `@prefix tree: <${TREE}> . @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix server: <http://localhost:8080/parcels/> .
server:by-time tree:viewDescription [ tree:pageSize "250"^^xsd:int ; tree:fragmentationStrategy ( [ tree:maxGranularity "day" ; a tree:HierarchicalTimeBasedFragmentation ] ) ] .`

describe('Turtle of definitions', () => {
  it('writes a graph the way a definition is written by hand', () => {
    expect(writeTurtle(parsed(VIEW))).toBe(`@prefix tree:   <${TREE}> .
@prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .
@prefix server: <http://localhost:8080/parcels/> .

server:by-time tree:viewDescription [
    tree:pageSize "250"^^xsd:int ;
    tree:fragmentationStrategy ( [
        a tree:HierarchicalTimeBasedFragmentation ;
        tree:maxGranularity "day"
    ] )
] .
`)
  })

  it('reads back what it writes', () => {
    const written = writeTurtle(parsed(VIEW))

    expect(writeTurtle(parsed(written))).toBe(written)
  })

  it('writes shared blank nodes and loose statements with a label', () => {
    const graph = parsed(`@prefix ex: <http://example.org/> .
      ex:a ex:knows _:shared . ex:b ex:knows _:shared . _:shared ex:name "Shared" .
      [] ex:name "Anonymous" .`)

    expect(writeTurtle(graph)).toBe(`@prefix ex: <http://example.org/> .

ex:a ex:knows _:b0 .

ex:b ex:knows _:b0 .

_:b0 ex:name "Shared" .

[] ex:name "Anonymous" .
`)
  })

  it('writes literals with what makes them differ', () => {
    const graph = parsed(`@prefix ex: <http://example.org/> . @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
      ex:a ex:text "say \\"hi\\"\\nnow" ; ex:label "Perceel"@nl ; ex:count 15 ; ex:on true ;
        ex:size "15"^^xsd:int ; ex:odd "1x"^^<http://example.org/type> .`)

    expect(writeTurtle(graph)).toContain(`ex:a ex:text "say \\"hi\\"\\nnow" ;
    ex:label "Perceel"@nl ;
    ex:count 15 ;
    ex:on true ;
    ex:size "15"^^xsd:int ;
    ex:odd "1x"^^ex:type .`)
  })

  it('replaces a value in its place and puts a new predicate where the order says', () => {
    const graph = parsed(`@prefix tree: <${TREE}> . @prefix ex: <http://example.org/> .
      ex:view tree:viewDescription ex:description .
      ex:description tree:fragmentationStrategy () ; tree:pageSize 250 .`)
    const description = namedNode('http://example.org/description')
    const order = [`${TREE}fragmentationStrategy`, 'https://w3id.org/ldes#retentionPolicy', `${TREE}pageSize`]

    const changed = setObjects(graph, description, `${TREE}fragmentationStrategy`, [namedNode('http://example.org/list')])
    const added = setObjects(changed, description, 'https://w3id.org/ldes#retentionPolicy',
      [namedNode('http://example.org/policy')], order)

    expect(writeTurtle(added)).toContain(`ex:description tree:fragmentationStrategy ex:list ;
    <https://w3id.org/ldes#retentionPolicy> ex:policy ;
    tree:pageSize 250 .`)
  })

  it('removes blank nodes that nothing refers to any longer', () => {
    const graph = parsed(VIEW)
    const description = objectOf(graph, namedNode('http://localhost:8080/parcels/by-time'), `${TREE}viewDescription`)!

    const emptied = setList(graph, description, `${TREE}fragmentationStrategy`, [])

    expect(listItems(emptied, objectOf(emptied, description, `${TREE}fragmentationStrategy`)!)).toEqual([])
    expect(emptied.quads).toHaveLength(3)
    expect(writeTurtle(emptied)).toContain('tree:fragmentationStrategy ()')
  })

  it('turns what is entered in a form into an IRI', () => {
    const graph = parsed(`@prefix tree: <${TREE}> .`)

    expect(iriFromText(graph, 'tree:value').iri).toBe(`${TREE}value`)
    expect(iriFromText(graph, '<http://example.org/x>').iri).toBe('http://example.org/x')
    const declared = iriFromText(graph, 'prov:generatedAtTime')
    expect(declared.iri).toBe('http://www.w3.org/ns/prov#generatedAtTime')
    expect(declared.graph.prefixes.prov).toBe('http://www.w3.org/ns/prov#')
    expect(iriFromText(graph, 'http://purl.org/dc/terms/created').graph.prefixes.dcterms)
      .toBe('http://purl.org/dc/terms/')
    expect(iriFromText(graph, 'nope:thing')).toEqual({ graph, iri: 'nope:thing' })
  })

  it('reports where Turtle does not parse', () => {
    expect(parseTurtle('<a> <b> .')).toEqual({ ok: false, error: expect.stringContaining('line 1') })
  })
})
