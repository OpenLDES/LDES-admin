import { PipelineSimulator } from '@/pipeline/simulator'
import { templateById } from '@/pipeline/templates'
import type { Pipeline } from '@/domain/ldio'

const NOW = new Date('2026-10-23T10:15:30Z')

describe('pipeline simulator', () => {
  const simulator = () => new PipelineSimulator(() => NOW)

  it('runs a member through every component of the parcels template', () => {
    const template = templateById('parcels-to-ldes')!

    const result = simulator().run(template.pipeline, template.sample)

    expect(result.delivered).toBe(true)
    expect(result.steps.map((step) => [step.kind, step.component, step.status])).toEqual([
      ['input', 'Ldio:HttpIn', 'ok'],
      ['adapter', 'Ldio:JsonToLdAdapter', 'ok'],
      ['transformer', 'Ldio:GeoJsonToWktTransformer', 'ok'],
      ['transformer', 'Ldio:VersionObjectCreator', 'ok'],
      ['output', 'Ldio:HttpOut', 'ok'],
      ['output', 'Ldio:ConsoleOut', 'ok'],
    ])
    expect(result.steps[2].summary).toBe('Converted 1 GeoJSON geometry to WKT')
    expect(result.member).toContain(
      '<https://data.vlaanderen.be/id/perceel/24062B0123-00A000/2026-10-23T08:12:00Z>',
    )
    expect(result.member).toContain(
      'dcterms:isVersionOf <https://data.vlaanderen.be/id/perceel/24062B0123-00A000>',
    )
    expect(result.member).toContain('prov:generatedAtTime "2026-10-23T08:12:00Z"^^xsd:dateTime')
    expect(result.member).toContain(
      'geo:asWKT "POLYGON ((4.7005 50.8798, 4.7012 50.8798, 4.7012 50.8803, 4.7005 50.8803, 4.7005 50.8798))"^^geo:wktLiteral',
    )
  })

  it('uses the current time when the observed date is not configured', () => {
    const pipeline: Pipeline = {
      name: 'versions',
      input: { name: 'Ldio:HttpIn', adapter: { name: 'Ldio:RdfAdapter' } },
      transformers: [
        { name: 'Ldio:VersionObjectCreator', config: { 'member-type': 'http://schema.org/Person' } },
      ],
      outputs: [{ name: 'Ldio:ConsoleOut' }],
    }

    const result = simulator().run(pipeline, templateById('http-to-console')!.sample)

    expect(result.member).toContain('<http://www.janedoe.com/#me/2026-10-23T10:15:30.000Z>')
  })

  it('drops a member without subject of the member type', () => {
    const pipeline: Pipeline = {
      name: 'versions',
      input: { name: 'Ldio:HttpIn', adapter: { name: 'Ldio:RdfAdapter' } },
      transformers: [
        { name: 'Ldio:VersionObjectCreator', config: { 'member-type': 'https://example.org/Car' } },
      ],
      outputs: [{ name: 'Ldio:ConsoleOut' }],
    }

    const result = simulator().run(pipeline, templateById('http-to-console')!.sample)

    expect(result.delivered).toBe(false)
    expect(result.steps.at(-1)).toMatchObject({
      component: 'Ldio:VersionObjectCreator',
      status: 'error',
      summary: 'No named subject of type <https://example.org/Car>: the member is dropped',
    })
  })

  it('materialises versions and filters unchanged members on the second run', () => {
    const template = templateById('replicate-ldes')!
    const sim = simulator()

    const first = sim.run(template.pipeline, template.sample)
    const second = sim.run(template.pipeline, template.sample)

    expect(first.delivered).toBe(true)
    expect(first.member).toContain('<https://data.vlaanderen.be/id/perceel/24062B0123-00A000>')
    expect(first.member).not.toContain('isVersionOf')
    expect(second.delivered).toBe(false)
    expect(second.steps.at(-1)).toMatchObject({
      component: 'Ldio:ChangeDetectionFilter',
      status: 'filtered',
    })
  })

  it('skolemises blank nodes and writes N-Quads', () => {
    const template = templateById('hindrances-geo')!

    const result = simulator().run(template.pipeline, template.sample)

    const skolemisation = result.steps.find((step) => step.component === 'Ldio:SkolemisationTransformer')
    expect(skolemisation?.summary).toBe('Replaced 1 blank node')
    expect(result.member).toContain('https://data.vlaanderen.be/.well-known/genid/')
    const console = result.steps.find((step) => step.component === 'Ldio:ConsoleOut')!
    expect(console.output).toMatch(/^<https:\/\/data\.vlaanderen\.be\/id\/hinder\/GIPOD-1287345> /)
    expect(console.output).toContain('"POINT (4.7253 50.8681)"^^<http://www.opengis.net/ont/geosparql#wktLiteral>')
  })

  it('stops when an input that needs an adapter has none', () => {
    const result = simulator().run(
      { name: 'raw', input: { name: 'Ldio:HttpIn' }, outputs: [{ name: 'Ldio:ConsoleOut' }] },
      { contentType: 'text/turtle', content: '<a> <b> <c> .' },
    )

    expect(result.delivered).toBe(false)
    expect(result.steps).toHaveLength(1)
    expect(result.steps[0].status).toBe('error')
  })

  it('reports a member that cannot be parsed', () => {
    const result = simulator().run(templateById('http-to-console')!.pipeline,
      { contentType: 'text/turtle', content: 'this is not turtle' })

    expect(result.steps.at(-1)).toMatchObject({ kind: 'adapter', status: 'error' })
    expect(result.steps.at(-1)?.summary).toMatch(/^Could not read the member/)
  })

  it('serialises JSON-LD with the used prefixes as context', () => {
    const pipeline: Pipeline = {
      name: 'json',
      input: { name: 'Ldio:HttpIn', adapter: { name: 'Ldio:RdfAdapter' } },
      outputs: [{ name: 'Ldio:ConsoleOut', config: { 'rdf-writer.content-type': 'application/ld+json' } }],
    }

    const output = simulator().run(pipeline, templateById('http-to-console')!.sample).steps.at(-1)!

    expect(output.format).toBe('json')
    expect(JSON.parse(output.output!)).toMatchObject({
      '@context': { rdf: expect.any(String), schema: 'http://schema.org/' },
      '@graph': [{ '@id': 'http://www.janedoe.com/#me', '@type': ['schema:Person'], 'schema:name': 'Jane Doe' }],
    })
  })
})
