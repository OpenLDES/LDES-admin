import { mount } from '@vue/test-utils'
import ComponentConfigForm from '@/components/sandbox/ComponentConfigForm.vue'
import { RDF_CONTENT_TYPES } from '@/pipeline/catalog'
import { withPrimeVue } from './primevue'

function form(component: string, config: Record<string, unknown>) {
  return mount(ComponentConfigForm, { ...withPrimeVue, props: { component, config } })
}

const lastChange = (wrapper: ReturnType<typeof form>) => wrapper.emitted('change')!.at(-1)![0]

describe('component configuration form', () => {
  it('lists the URLs of an LDES client, to change, add and remove', async () => {
    const wrapper = form('Ldio:LdesClient', { urls: ['http://ldes.example.org/parcels/by-page'] })
    const urls = wrapper.find('[data-cy="config-urls"]')

    expect(urls.findAll('[data-cy=list-item]').map((item) => (item.element as HTMLInputElement).value))
      .toEqual(['http://ldes.example.org/parcels/by-page'])

    await urls.find('[data-cy=add-item]').trigger('click')
    expect(lastChange(wrapper)).toEqual({ urls: ['http://ldes.example.org/parcels/by-page', ''] })

    await urls.find('[data-cy=list-item]').setValue('http://ldes.example.org/parcels/by-time')
    expect(lastChange(wrapper)).toEqual({ urls: ['http://ldes.example.org/parcels/by-time'] })

    await urls.find('[data-cy=remove-item]').trigger('click')
    expect(lastChange(wrapper)).toEqual({})
  })

  it('shows what is wrong with a URL at its line', () => {
    const wrapper = form('Ldio:LdesClient', { urls: ['http://ldes.example.org/parcels', 'not a url'] })
    const items = wrapper.findAll('[data-cy="config-urls"] .list-item')

    expect(items[0].text()).not.toContain('Not an http(s) URL')
    expect(items[1].text()).toContain('Not an http(s) URL')
    expect(items[1].find('input').classes()).toContain('p-invalid')
  })

  it('offers the content types Jena reads as source format', () => {
    const wrapper = form('Ldio:LdesClient', { urls: ['http://x.org'] })
    const select = wrapper.findComponent({ name: 'Select' })

    expect(select.props('options')).toEqual(RDF_CONTENT_TYPES)
    expect(select.props('placeholder')).toBe('Default: text/turtle')
  })

  it('writes the chosen source format back, under the key the configuration uses', async () => {
    const wrapper = form('Ldio:LdesClient', { urls: ['http://x.org'], sourceFormat: 'text/turtle' })

    wrapper.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', 'application/n-quads')

    expect(lastChange(wrapper)).toEqual({ urls: ['http://x.org'], sourceFormat: 'application/n-quads' })
  })

  it('collapses groups until they are used, and shows what applies', async () => {
    const wrapper = form('Ldio:LdesClient', { urls: ['http://x.org'], auth: { type: 'API_KEY' } })

    // Authentication holds a value and a problem, so it is open; State is not used
    expect(wrapper.find('[data-cy="group-Authentication"]').attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('[data-cy="config-auth.api-key"]').text()).toContain('Needed for API key authentication')
    expect(wrapper.find('[data-cy="config-auth.client-id"]').exists()).toBe(false)
    expect(wrapper.find('[data-cy="config-state"]').exists()).toBe(false)

    await wrapper.find('[data-cy="group-State"]').trigger('click')
    expect(wrapper.find('[data-cy="config-state"]').exists()).toBe(true)
    expect(wrapper.find('[data-cy="config-sqlite.directory"]').exists()).toBe(false)
  })

  it('lists keys that are not properties, to remove them', async () => {
    const wrapper = form('Ldio:LdesClient', { urls: ['http://x.org'], sourceFormatt: 'text/turtle' })

    expect(wrapper.find('[data-cy=other-properties]').text()).toContain('sourceFormatt: text/turtle')

    await wrapper.find('[data-cy=other-properties] button').trigger('click')
    expect(lastChange(wrapper)).toEqual({ urls: ['http://x.org'] })
  })

  it('marks a missing required property', () => {
    const wrapper = form('Ldio:HttpOut', {})

    expect(wrapper.find('[data-cy="config-endpoint"]').text()).toContain('Required')
  })
})
