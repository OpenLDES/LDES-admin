import { fragmentationKinds, retentionPolicyKinds, viewDescriptionProperties } from '@/ldes/catalog'
import { changeKind, type DefinitionProblem, kindOf, readValue, writeValue } from '@/ldes/definition'
import { parseTurtle, type RdfGraph, writeTurtle } from '@/ldes/turtle'
import {
  addFragmentation,
  addViewRetentionPolicy,
  descriptionOf,
  fragmentationsOf,
  moveFragmentation,
  removeFragmentation,
  removeViewRetentionPolicy,
  renameView,
  viewNameOf,
  viewPresets,
  viewProblems,
  viewRetentionPolicies,
  viewTemplate,
  writeDescriptionValue,
} from '@/ldes/view'

const STREAM_URL = 'http://localhost:8080/parcels'
const STREAM = { timestampPath: 'http://www.w3.org/ns/prov#generatedAtTime' }
const [timeBased, geospatial, reference] = fragmentationKinds
const [durationAgo, latestVersions] = retentionPolicyKinds

const preset = (id: string) => viewTemplate(viewPresets.find((p) => p.id === id)!, STREAM_URL, STREAM)
const property = (kind: typeof timeBased, key: string) => kind.properties.find((p) => p.key === key)!
const messages = (problems: DefinitionProblem[]) => problems.map((problem) => `${problem.key ?? ''}: ${problem.message}`)

function parsed(body: string): RdfGraph {
  const result = parseTurtle(`@prefix tree: <https://w3id.org/tree#> . @prefix ldes: <https://w3id.org/ldes#> .
    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> . @prefix server: <${STREAM_URL}/> . ${body}`)
  if (!result.ok) throw new Error(result.error)
  return result.graph
}

describe('view definitions', () => {
  it('starts from presets that the server accepts', () => {
    for (const { id } of viewPresets) expect(viewProblems(preset(id))).toEqual([])
    expect(writeTurtle(preset('time'))).toContain(`server:by-time tree:viewDescription [
    tree:fragmentationStrategy ( [
        a tree:HierarchicalTimeBasedFragmentation ;
        tree:maxGranularity "day" ;
        tree:fragmentationPath prov:generatedAtTime
    ] ) ;
    tree:pageSize "250"^^xsd:int
] .`)
  })

  it('changes the maximum granularity, and only to one the server knows', () => {
    const graph = preset('time')
    const [fragmentation] = fragmentationsOf(graph)!
    const granularity = property(timeBased, 'tree:maxGranularity')

    const hourly = writeValue(graph, fragmentation, granularity, 'hour')
    expect(readValue(hourly, fragmentation, granularity)).toBe('hour')
    expect(writeTurtle(hourly)).toContain('tree:maxGranularity "hour"')

    const weekly = writeValue(graph, fragmentation, granularity, 'week')
    expect(messages(viewProblems(weekly)))
      .toEqual(['tree:maxGranularity: Choose one of year, month, day, hour, minute, second'])
  })

  it('wants the datatypes the server checks, and the form writes them', () => {
    const graph = parsed(`server:tiles tree:viewDescription [ tree:fragmentationStrategy ( [
      a tree:GeospatialFragmentation ; tree:maxZoom "15"^^xsd:int ; tree:fragmentationPath <http://www.opengis.net/ont/geosparql#asWKT> ] ) ] .`)
    const [fragmentation] = fragmentationsOf(graph)!
    const zoom = property(geospatial, 'tree:maxZoom')

    expect(messages(viewProblems(graph))).toEqual(['tree:maxZoom: Must be typed xsd:integer, not xsd:int'])
    expect(viewProblems(writeValue(graph, fragmentation, zoom, '15'))).toEqual([])
    expect(messages(viewProblems(writeValue(graph, fragmentation, zoom, '24'))))
      .toEqual(['tree:maxZoom: Enter a number from 0 to 23'])
  })

  it('keeps fragmentations in order, to move and remove', () => {
    let graph = addFragmentation(preset('time'), reference, STREAM)
    graph = addFragmentation(graph, geospatial, STREAM)
    const kinds = () => fragmentationsOf(graph)!.map((node) => kindOf(graph, node, fragmentationKinds)?.label)
    expect(kinds()).toEqual(['Time-based', 'Reference', 'Geospatial'])

    graph = moveFragmentation(graph, 2, -1)
    expect(kinds()).toEqual(['Time-based', 'Geospatial', 'Reference'])

    graph = removeFragmentation(graph, 0)
    expect(kinds()).toEqual(['Geospatial', 'Reference'])
    expect(writeTurtle(graph)).not.toContain('maxGranularity')
  })

  it('keeps the path when a fragmentation becomes another kind', () => {
    const graph = preset('time')
    const [fragmentation] = fragmentationsOf(graph)!

    const changed = changeKind(graph, fragmentation, timeBased, reference, STREAM)

    const path = property(reference, 'tree:fragmentationPath')
    expect(kindOf(changed, fragmentation, fragmentationKinds)).toBe(reference)
    expect(readValue(changed, fragmentation, path)).toBe('prov:generatedAtTime')
    expect(readValue(changed, fragmentation, property(timeBased, 'tree:maxGranularity'))).toBeUndefined()
    expect(viewProblems(changed)).toEqual([])
  })

  it('allows one retention policy of each kind', () => {
    let graph = addViewRetentionPolicy(preset('pagination'), durationAgo)
    graph = addViewRetentionPolicy(graph, latestVersions)
    expect(viewProblems(graph)).toEqual([])

    graph = addViewRetentionPolicy(graph, durationAgo)
    expect(messages(viewProblems(graph))).toEqual([': There can be only one time-based retention policy'])

    graph = removeViewRetentionPolicy(graph, viewRetentionPolicies(graph)[2])
    expect(viewRetentionPolicies(graph)).toHaveLength(2)
    expect(viewProblems(graph)).toEqual([])
  })

  it('wants an xsd:duration without weeks', () => {
    const graph = parsed(`server:recent tree:viewDescription [ tree:fragmentationStrategy () ;
      ldes:retentionPolicy [ a ldes:DurationAgoPolicy ; tree:value "P30D" ] ] .`)
    const [policy] = viewRetentionPolicies(graph)
    const value = property(durationAgo, 'tree:value')

    expect(messages(viewProblems(graph))).toEqual(['tree:value: Must be typed xsd:duration, not xsd:string'])
    expect(messages(viewProblems(writeValue(graph, policy, value, 'P1W'))))
      .toEqual(['tree:value: Enter an xsd:duration, e.g. P30D, PT12H or P1Y2M (no weeks)'])
    expect(writeTurtle(writeValue(graph, policy, value, 'PT12H'))).toContain('tree:value "PT12H"^^xsd:duration')
  })

  it('renames the view, to a name the server keeps', () => {
    const graph = renameView(preset('pagination'), 'all-parcels')
    expect(viewNameOf(graph)).toBe('all-parcels')
    expect(writeTurtle(graph)).toContain('server:all-parcels tree:viewDescription')

    expect(messages(viewProblems(renameView(graph, '2026'))))
      .toEqual(['name: Start with a letter: the server would drop leading digits'])
    expect(messages(viewProblems(renameView(graph, 'by page'))))
      .toEqual(['name: Use letters, digits, -, _ and . only, starting with a letter'])
  })

  it('sets the page size, keeping its datatype', () => {
    const pageSize = viewDescriptionProperties[0]
    const graph = writeDescriptionValue(preset('pagination'), pageSize, '100')

    expect(readValue(graph, descriptionOf(graph), pageSize)).toBe('100')
    expect(writeTurtle(graph)).toContain('tree:pageSize "100"^^xsd:int')
    expect(messages(viewProblems(writeDescriptionValue(graph, pageSize, '0')))).toEqual(['tree:pageSize: Enter 1 or more'])
  })

  it('reports what the server refuses or ignores', () => {
    const graph = parsed(`server:odd tree:viewDescription [ tree:pageSize 10 ; tree:pageSise 20 ] .
      server:pages tree:viewDescription [ tree:fragmentationStrategy ( [ a tree:PaginationFragmentation ]
        [ a tree:FooFragmentation ] ) ] .`)

    expect(messages(viewProblems(graph))).toEqual([
      ': Define one view at a time',
      'tree:pageSise: Not a property of a view description; the server ignores it',
      'tree:fragmentationStrategy: Required, even without fragmentations: tree:fragmentationStrategy ()',
    ])
    expect(messages(viewProblems(parsed(`server:pages tree:viewDescription [ tree:fragmentationStrategy (
      [ a tree:PaginationFragmentation ] [ a tree:FooFragmentation ] ) ] .`)))).toEqual([
      ': Leave pagination out: the server paginates every view',
      ': Not a fragmentation the server knows: use tree:HierarchicalTimeBasedFragmentation, tree:GeospatialFragmentation, tree:ReferenceFragmentation',
    ])
  })
})
