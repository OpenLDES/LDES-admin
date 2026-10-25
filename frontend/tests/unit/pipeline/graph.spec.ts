import {
  autoLayout,
  connectionProblem,
  createNode,
  edgeId,
  type GraphEdge,
  graphToPipeline,
  pipelineToGraph,
} from '@/pipeline/graph'
import { templates } from '@/pipeline/templates'
import type { Pipeline } from '@/domain/ldio'

const connect = (source: string, target: string): GraphEdge => ({
  id: edgeId(source, target),
  source,
  target,
})

describe('pipeline graph', () => {
  it.each(templates.map((template) => [template.id, template.pipeline]))(
    'round-trips the %s template',
    (_id, pipeline) => {
      const { nodes, edges } = pipelineToGraph(pipeline as Pipeline)

      const conversion = graphToPipeline(pipeline as Pipeline, nodes, edges)

      expect(conversion.issues.filter((issue) => issue.severity === 'error')).toEqual([])
      expect(conversion.valid).toBe(true)
      expect(conversion.pipeline).toEqual({
        ...(pipeline as Pipeline),
        input: { ...(pipeline as Pipeline).input },
      })
    },
  )

  it('draws the chain top to bottom with the outputs side by side', () => {
    const { nodes, edges } = pipelineToGraph(templates[0].pipeline)

    expect(nodes.map((node) => [node.id, node.position])).toEqual([
      ['input', { x: 0, y: 0 }],
      ['adapter', { x: 0, y: 110 }],
      ['transformer-1', { x: 0, y: 220 }],
      ['transformer-2', { x: 0, y: 330 }],
      ['output-1', { x: -120, y: 440 }],
      ['output-2', { x: 120, y: 440 }],
    ])
    expect(edges.map((edge) => `${edge.source}>${edge.target}`)).toEqual([
      'input>adapter',
      'adapter>transformer-1',
      'transformer-1>transformer-2',
      'transformer-2>output-1',
      'transformer-2>output-2',
    ])
  })

  it('draws a pipeline left to right for previews', () => {
    const { nodes } = pipelineToGraph(templates[0].pipeline, 'LR')

    expect(nodes.map((node) => node.position)).toEqual([
      { x: 0, y: 0 },
      { x: 260, y: 0 },
      { x: 520, y: 0 },
      { x: 780, y: 0 },
      { x: 1040, y: -60 },
      { x: 1040, y: 60 },
    ])
    expect(nodes.every((node) => node.data.direction === 'LR')).toBe(true)
  })

  it('requires exactly one input and at least one output', () => {
    const transformer = createNode('Ldio:SkolemisationTransformer', { x: 0, y: 0 }, undefined, 't')

    const conversion = graphToPipeline({ name: 'empty' }, [transformer], [])

    expect(conversion.valid).toBe(false)
    expect(conversion.issues.map((issue) => issue.message)).toContain(
      'Add an input: every pipeline starts with one',
    )
  })

  it('requires an adapter for inputs that receive raw data', () => {
    const nodes = [
      createNode('Ldio:HttpIn', { x: 0, y: 0 }, undefined, 'in'),
      createNode('Ldio:ConsoleOut', { x: 300, y: 0 }, undefined, 'out'),
    ]

    const conversion = graphToPipeline({ name: 'no-adapter' }, nodes, [connect('in', 'out')])

    expect(conversion.valid).toBe(false)
    expect(conversion.issues).toContainEqual({
      severity: 'error',
      message: 'HTTP In receives raw data and needs an adapter',
      nodeId: 'in',
    })
  })

  it('accepts an LDES client without adapter and warns about loose components', () => {
    const nodes = [
      createNode('Ldio:LdesClient', { x: 0, y: 0 }, undefined, 'in'),
      createNode('Ldio:ConsoleOut', { x: 300, y: 0 }, undefined, 'out'),
      createNode('Ldio:SkolemisationTransformer', { x: 0, y: 200 }, undefined, 'loose'),
    ]

    const conversion = graphToPipeline({ name: 'replication' }, nodes, [connect('in', 'out')])

    expect(conversion.valid).toBe(true)
    expect(conversion.pipeline?.outputs).toEqual([{ name: 'Ldio:ConsoleOut' }])
    expect(conversion.issues).toContainEqual({
      severity: 'warning',
      message: 'Skolemisation is not connected to the pipeline',
      nodeId: 'loose',
    })
  })

  it('reports missing required configuration', () => {
    const nodes = [
      createNode('Ldio:LdesClient', { x: 0, y: 0 }, {}, 'in'),
      createNode('Ldio:NoopOut', { x: 300, y: 0 }, undefined, 'out'),
    ]

    const conversion = graphToPipeline({ name: 'p' }, nodes, [connect('in', 'out')])

    expect(conversion.issues).toEqual([
      { severity: 'error', message: "LDES Client needs 'urls'", nodeId: 'in', property: 'urls' },
    ])
    expect(conversion.valid).toBe(false)
    // The simulation does not need it
    expect(conversion.runnable).toBe(true)
  })

  it('reports invalid configuration values with the property they are about', () => {
    const nodes = [
      createNode('Ldio:LdesClient', { x: 0, y: 0 }, { urls: ['http://ldes.example.org/parcels', 'nope'] }, 'in'),
      createNode('Ldio:NoopOut', { x: 300, y: 0 }, undefined, 'out'),
    ]

    const conversion = graphToPipeline({ name: 'p' }, nodes, [connect('in', 'out')])

    expect(conversion.issues).toEqual([{
      severity: 'error',
      message: 'LDES Client – urls (line 2): Not an http(s) URL',
      nodeId: 'in',
      property: 'urls',
    }])
  })

  it('rejects pipeline names that cannot be used in URLs', () => {
    const { nodes, edges } = pipelineToGraph(templates[1].pipeline)

    expect(graphToPipeline({ name: 'my pipeline' }, nodes, edges).valid).toBe(false)
  })

  describe('connections', () => {
    const input = createNode('Ldio:HttpIn', { x: 0, y: 0 }, undefined, 'in')
    const adapter = createNode('Ldio:RdfAdapter', { x: 0, y: 0 }, undefined, 'adapter')
    const first = createNode('Ldio:SkolemisationTransformer', { x: 0, y: 0 }, undefined, 't1')
    const second = createNode('Ldio:ChangeDetectionFilter', { x: 0, y: 0 }, undefined, 't2')
    const output = createNode('Ldio:ConsoleOut', { x: 0, y: 0 }, undefined, 'out')

    it('allows connecting an input to its adapter', () => {
      expect(connectionProblem(input, adapter, [])).toBeUndefined()
    })

    it('only lets an adapter follow an input', () => {
      expect(connectionProblem(first, adapter, [])).toBe('An adapter can only follow an input')
    })

    it('does not let anything follow an output', () => {
      expect(connectionProblem(output, first, [])).toBe('An output ends the pipeline')
    })

    it('allows one incoming connection per component', () => {
      expect(connectionProblem(second, output, [connect('t1', 'out')])).toBe(
        'Console Out already has an incoming connection',
      )
    })

    it('prevents loops', () => {
      expect(connectionProblem(second, first, [connect('t1', 't2')])).toBe('Pipelines cannot loop')
    })
  })

  it('lays out a graph in pipeline order', () => {
    const { nodes, edges } = pipelineToGraph(templates[2].pipeline)
    const scrambled = nodes.map((node, i) => ({ ...node, position: { x: i * 7, y: i * 13 } }))

    const laidOut = autoLayout(scrambled, graphToPipeline({ name: 'p' }, scrambled, edges))

    expect(laidOut.map((node) => node.position)).toEqual(nodes.map((node) => node.position))
  })
})
