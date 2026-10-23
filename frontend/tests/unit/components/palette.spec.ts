import { mount } from '@vue/test-utils'
import ComponentPalette from '@/components/sandbox/ComponentPalette.vue'
import { components } from '@/pipeline/catalog'
import { withPrimeVue } from './primevue'

describe('component palette', () => {
  it('lists every component, grouped by kind', () => {
    const palette = mount(ComponentPalette, withPrimeVue)

    expect(palette.findAll('.group-title').map((title) => title.text().replace(/\s+\d+$/, '')))
      .toEqual(['Inputs', 'Adapters', 'Transformers', 'Outputs'])
    expect(palette.findAll('.item')).toHaveLength(components.length)
  })

  it('finds components by name, id or description', async () => {
    const palette = mount(ComponentPalette, withPrimeVue)

    await palette.find('input').setValue('version')

    expect(palette.findAll('.item-label').map((label) => label.text()))
      .toEqual(['Version Object Creator', 'Version Materialiser'])
  })

  it('adds a component on double click', async () => {
    const palette = mount(ComponentPalette, withPrimeVue)

    await palette.find('[data-cy="palette-Ldio:HttpOut"]').trigger('dblclick')

    expect(palette.emitted('add')).toEqual([['Ldio:HttpOut']])
  })

  it('carries the component id when dragged', async () => {
    const palette = mount(ComponentPalette, withPrimeVue)
    const data = new Map<string, string>()
    const dataTransfer = { setData: (type: string, value: string) => data.set(type, value), effectAllowed: '' }

    await palette.find('[data-cy="palette-Ldio:LdesClient"]').trigger('dragstart', { dataTransfer })

    expect(data.get('application/ldio-component')).toBe('Ldio:LdesClient')
  })
})
