import { exampleFor, examples, exampleViews } from '@/examples'
import { parking } from '@/examples/parking'
import { sensors } from '@/examples/sensors'
import type { EventStream } from '@/domain/ldes'
import { PREFIXES } from '@/domain/rdf'
import { hasErrors } from '@/ldes/definition'
import { memberTemplate } from '@/ldes/memberTemplates'
import { collectionOf, eventStreamProblems, renameCollection } from '@/ldes/eventStream'
import { objectOf, parseTurtle, type RdfGraph, subjectsWith, writeTurtle } from '@/ldes/turtle'
import { fragmentationsOf, viewNameOf, viewProblems } from '@/ldes/view'
import { graphToPipeline, pipelineToGraph } from '@/pipeline/graph'
import { PipelineSimulator } from '@/pipeline/simulator'

const NOW = new Date('2026-10-25T13:20:00Z')
const SERVER = 'http://localhost:8080'

function parse(turtle: string): RdfGraph {
  const parsed = parseTurtle(turtle)
  if (!parsed.ok) throw new Error(parsed.error)
  return parsed.graph
}

function stream(example: typeof sensors, collection = example.collection): EventStream {
  return {
    collection,
    timestampPath: example.timestampPath,
    versionOfPath: `${PREFIXES.dcterms}isVersionOf`,
    createVersions: false,
    views: [],
    definition: example.eventStream(SERVER, collection),
  }
}

describe.each(examples.map((example) => [example.id, example] as const))('the %s example', (_, example) => {
  it('defines an event stream the server accepts, also under another name', () => {
    const graph = parse(example.eventStream(SERVER, example.collection))

    expect(collectionOf(graph)).toBe(example.collection)
    expect(eventStreamProblems(graph)).toEqual([])

    const renamed = parse(writeTurtle(renameCollection(graph, 'demo')))
    expect(collectionOf(renamed)).toBe('demo')
    expect(eventStreamProblems(renamed)).toEqual([])
  })

  it('has a paginated and a time-based view on the timestamp path', () => {
    const views = exampleViews(`${SERVER}/${example.collection}`, example.timestampPath).map(({ definition }) => parse(definition))

    expect(views.map(viewNameOf)).toEqual(['by-page', 'by-time'])
    views.forEach((view) => expect(hasErrors(viewProblems(view))).toBe(false))
    expect(fragmentationsOf(views[0])).toEqual([])
    const [timeBased] = fragmentationsOf(views[1])!
    expect(objectOf(views[1], timeBased, `${PREFIXES.tree}fragmentationPath`)?.value).toBe(example.timestampPath)
  })

  it('has a valid pipeline that posts to the event stream', () => {
    const pipeline = example.pipeline('demo', 'http://sandbox-ldes-server:8080/demo')
    const { nodes, edges } = pipelineToGraph(pipeline)

    const conversion = graphToPipeline(pipeline, nodes, edges)

    expect(conversion.issues).toEqual([])
    expect(pipeline.name).toBe('demo-ingest')
    expect(pipeline.outputs?.[0].config?.endpoint).toBe('http://sandbox-ldes-server:8080/demo')
  })

  it('has members that are version objects, as the pipeline makes them', () => {
    const member = example.member(4, NOW)
    const graph = parse(member.content)
    const named = graph.quads.map((q) => q.subject).filter((subject) => subject.termType === 'NamedNode')

    // The server takes one member at a time
    expect(new Set(named.map((subject) => subject.value))).toEqual(new Set([member.id]))
    expect(member.id).toBe(`${member.isVersionOf}/2026-10-25T13:20:00Z`)
    expect(objectOf(graph, named[0], `${PREFIXES.dcterms}isVersionOf`)?.value).toBe(member.isVersionOf)
    const timestamp = objectOf(graph, named[0], example.timestampPath)
    expect(timestamp?.value).toBe('2026-10-25T13:20:00Z')
    expect(timestamp?.termType === 'Literal' && timestamp.datatype.value).toBe(`${PREFIXES.xsd}dateTime`)
    expect(subjectsWith(graph, `${PREFIXES.rdf}type`, example.memberClass)).toHaveLength(1)
  })

  it('offers its members to ingest in its event stream, also under another name', () => {
    const index = Math.floor(NOW.getTime() / 60_000) % 997

    expect(memberTemplate(stream(example, 'renamed'), NOW)).toBe(example.member(index, NOW).content)
  })

  it('is recognised in event streams that hold its members', () => {
    expect(exampleFor(stream(example))).toBe(example)
    expect(exampleFor(stream(example, 'renamed'))).toBe(example)
    expect(exampleFor({ ...stream(example, 'renamed'), definition: '' })).toBeUndefined()
    expect(exampleFor({ ...stream(example), timestampPath: `${PREFIXES.prov}generatedAtTime` })).toBeUndefined()
  })
})

describe('the sensor example', () => {
  it('comes with observations of every property at every station, for the last four hours', () => {
    const members = sensors.sampleData!.members(NOW)

    expect(members).toHaveLength(36)
    expect(members[0]).toContain('sosa:resultTime "2026-10-25T10:00:00Z"^^xsd:dateTime')
    expect(members[35]).toContain('sosa:resultTime "2026-10-25T13:00:00Z"^^xsd:dateTime')
    expect(new Set(members).size).toBe(36)
    members.forEach((member) => expect(member).toMatch(/qudt:numericValue "-?\d+(\.\d)?"\^\^xsd:double/))
  })

  it('sends the same observations to the pipeline as JSON, which the context maps onto SOSA', () => {
    const message = sensors.sampleData!.message(NOW)
    const readings = JSON.parse(message.content) as Record<string, unknown>[]

    expect(message.contentType).toBe('application/json')
    expect(readings).toHaveLength(36)
    const context = JSON.parse(String(sensors.pipeline('demo', 'http://x/demo').input.adapter?.config?.context))['@context']
    readings.forEach((reading) => Object.keys(reading).forEach((key) => expect(context).toHaveProperty(key)))
  })

  it('turns a reading into a version object in the simulator', () => {
    const message = JSON.parse(sensors.sampleData!.message(NOW).content)[0]
    const result = new PipelineSimulator(() => NOW).run(sensors.pipeline('demo', 'http://x/demo'),
      { contentType: 'application/json', content: JSON.stringify(message) })

    expect(result.delivered).toBe(true)
    expect(result.member).toContain(`<${message.id}/2026-10-25T10:00:00Z> a sosa:Observation`)
    expect(result.member).toContain(`qudt:unit <${message.hasResult.unit}>`)
    expect(result.member.match(/sosa:resultTime/g)).toHaveLength(1)
  })
})

describe('the parking example', () => {
  it('polls the open data of Ghent, like the onboarding example', () => {
    const pipeline = parking.pipeline('demo', 'http://x/demo')

    expect(pipeline.input.name).toBe('Ldio:HttpInPoller')
    expect(String(pipeline.input.config?.url)).toContain('data.stad.gent')
    expect(pipeline.input.adapter?.name).toBe('Ldio:RmlAdapter')
    expect(pipeline.transformers?.map((transformer) => transformer.name))
      .toEqual(['Ldio:SparqlConstructTransformer', 'Ldio:VersionObjectCreator'])
    expect(parking.sampleData).toBeUndefined()
  })
})
