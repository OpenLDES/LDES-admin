import { mount } from '@vue/test-utils'
import DefinitionFields from '@/components/ldes/DefinitionFields.vue'
import ResourceCard from '@/components/ldes/ResourceCard.vue'
import { eventStreamProperties, fragmentationKinds } from '@/ldes/catalog'
import { kindOf, readValue } from '@/ldes/definition'
import { eventStreamProblems, eventStreamTemplate, streamOf, writeStreamValue } from '@/ldes/eventStream'
import { parseTurtle, type RdfGraph, type Resource, writeTurtle } from '@/ldes/turtle'
import { fragmentationsOf, viewPresets, viewProblems, viewTemplate } from '@/ldes/view'
import { withPrimeVue } from './primevue'

const STREAM = { timestampPath: 'http://purl.org/dc/terms/created' }

function card(graph: RdfGraph) {
  const [fragmentation] = fragmentationsOf(graph)!
  const wrapper = mount(ResourceCard, {
    ...withPrimeVue,
    props: {
      graph,
      node: fragmentation,
      kinds: fragmentationKinds,
      noun: 'fragmentation',
      problems: viewProblems(graph),
      stream: STREAM,
      position: { index: 0, count: 1 },
    },
  })
  return { wrapper, fragmentation }
}

const changed = (wrapper: ReturnType<typeof card>['wrapper']) => wrapper.emitted('change')!.at(-1)![0] as RdfGraph

function timeBased(granularity = '"day"', extra = '') {
  const result = parseTurtle(`@prefix tree: <https://w3id.org/tree#> . @prefix server: <http://localhost:8080/parcels/> .
    server:by-time tree:viewDescription [ tree:fragmentationStrategy ( [ a tree:HierarchicalTimeBasedFragmentation ;
      tree:maxGranularity ${granularity} ; tree:fragmentationPath <http://purl.org/dc/terms/created> ${extra} ] ) ] .`)
  if (!result.ok) throw new Error(result.error)
  return result.graph
}

describe('definition forms', () => {
  it('offers the granularities of a time-based fragmentation in a dropdown, and writes the choice', () => {
    const { wrapper } = card(viewTemplate(viewPresets[1], 'http://localhost:8080/parcels', STREAM))
    const select = wrapper.find('[data-cy="config-tree:maxGranularity"]').findComponent({ name: 'Select' })

    expect(select.props('options').map((option: { value: string }) => option.value))
      .toEqual(['year', 'month', 'day', 'hour', 'minute', 'second'])
    expect(select.props('modelValue')).toBe('day')

    select.vm.$emit('update:modelValue', 'hour')
    expect(writeTurtle(changed(wrapper))).toContain('tree:maxGranularity "hour"')
  })

  it('shows a granularity the server does not know at its field', () => {
    const { wrapper } = card(timeBased('"week"'))

    expect(wrapper.find('[data-cy="config-tree:maxGranularity"]').text())
      .toContain('Choose one of year, month, day, hour, minute, second')
  })

  it('turns the fragmentation into another kind', () => {
    const { wrapper, fragmentation } = card(timeBased())

    wrapper.find('[data-cy=resource-kind]').findComponent({ name: 'Select' }).vm
      .$emit('update:modelValue', 'https://w3id.org/tree#GeospatialFragmentation')

    const graph = changed(wrapper)
    expect(kindOf(graph, fragmentation, fragmentationKinds)?.label).toBe('Geospatial')
    expect(writeTurtle(graph)).toContain('tree:maxZoom 15')
  })

  it('lists predicates that are no property, to remove them', async () => {
    const { wrapper } = card(timeBased('"day"', '; tree:maxGranularty "hour"'))

    expect(wrapper.find('[data-cy=other-properties]').text()).toContain('tree:maxGranularty hour')
    await wrapper.find('[data-cy=other-properties] button').trigger('click')
    expect(writeTurtle(changed(wrapper))).not.toContain('maxGranularty')
  })

  it('shows the version delimiter once the server creates versions', () => {
    const fields = (graph: RdfGraph) => mount(DefinitionFields, {
      ...withPrimeVue,
      props: {
        graph,
        node: streamOf(graph) as Resource,
        properties: eventStreamProperties,
        problems: eventStreamProblems(graph),
        owner: 'an event stream',
      },
    })
    const graph = eventStreamTemplate('http://localhost:8080', 'parcels')
    expect(fields(graph).find('[data-cy="config-ldes:versionDelimiter"]').exists()).toBe(false)

    const versioned = writeStreamValue(graph, eventStreamProperties.find((p) => p.key === 'ldes:createVersions')!, true)
    expect(readValue(versioned, streamOf(versioned), eventStreamProperties[2])).toBe('true')
    expect(fields(versioned).find('[data-cy="config-ldes:versionDelimiter"]').exists()).toBe(true)
  })
})
