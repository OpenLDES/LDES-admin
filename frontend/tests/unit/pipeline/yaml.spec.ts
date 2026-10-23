import {
  componentToYaml,
  parseComponentYaml,
  parsePipelineYaml,
  pipelineToYaml,
} from '@/pipeline/yaml'
import { templates } from '@/pipeline/templates'

describe('pipeline YAML', () => {
  it('writes a pipeline in the LDIO format', () => {
    expect(pipelineToYaml(templates[1].pipeline)).toBe(`name: data
description: Reads incoming RDF over HTTP and writes it to the console
input:
  name: Ldio:HttpIn
  adapter:
    name: Ldio:RdfAdapter
outputs:
  - name: Ldio:ConsoleOut
    config:
      rdf-writer:
        content-type: application/n-quads
`)
  })

  it.each(templates.map((template) => [template.id, template]))(
    'reads back the %s template',
    (_id, template) => {
      const parsed = parsePipelineYaml(pipelineToYaml(template.pipeline))

      expect(parsed.ok).toBe(true)
      if (parsed.ok) {
        expect(parsed.pipeline.name).toBe(template.pipeline.name)
        expect(parsed.pipeline.input.adapter?.name).toBe(template.pipeline.input.adapter?.name)
        expect(parsed.pipeline.outputs).toHaveLength(template.pipeline.outputs!.length)
      }
    },
  )

  it('reports unknown components in a pipeline', () => {
    const parsed = parsePipelineYaml(`name: p
input:
  name: Ldio:Telepathy
outputs:
  - name: Ldio:ConsoleOut`)

    expect(parsed).toEqual({ ok: false, error: "Unknown component 'Ldio:Telepathy'" })
  })

  it('writes a single component with its config', () => {
    expect(componentToYaml('Ldio:HttpOut', { endpoint: 'http://localhost:8080/parcels' }))
      .toBe(`name: Ldio:HttpOut
config:
  endpoint: http://localhost:8080/parcels
`)
  })

  it('reads a single component', () => {
    expect(parseComponentYaml(`name: Ldio:HttpOut
config:
  endpoint: http://localhost:8080/parcels
  rdf-writer:
    content-type: application/n-quads`, 'output')).toEqual({
      ok: true,
      name: 'Ldio:HttpOut',
      config: {
        endpoint: 'http://localhost:8080/parcels',
        'rdf-writer': { 'content-type': 'application/n-quads' },
      },
    })
  })

  it('defaults a missing config to an empty one', () => {
    expect(parseComponentYaml('name: Ldio:ConsoleOut')).toEqual({
      ok: true,
      name: 'Ldio:ConsoleOut',
      config: {},
    })
  })

  it.each([
    ['name: [unclosed', 'Flow sequence in block collection'],
    ['- just a list', "Expected a 'name' and optionally a 'config'"],
    ['config: {}', "'name' must be the id of a component, e.g. Ldio:HttpOut"],
    ['name: Ldio:ConsoleOut\nconfig: text', "'config' must be a map of properties"],
    ['name: Ldio:HttpIn\nadapter:\n  name: Ldio:RdfAdapter', 'The adapter is a separate component: connect it to the input'],
    ['name: Ldio:HttpOut\nendpoint: x', "Unexpected key 'endpoint'"],
  ])('explains what is wrong with %j', (text, error) => {
    const parsed = parseComponentYaml(text, 'input')

    expect(parsed.ok).toBe(false)
    if (!parsed.ok) expect(parsed.error).toContain(error)
  })

  it('does not turn a component into one of another kind', () => {
    expect(parseComponentYaml('name: Ldio:HttpOut', 'transformer')).toEqual({
      ok: false,
      error: 'Ldio:HttpOut is an output, but this component is a transformer',
    })
  })
})
