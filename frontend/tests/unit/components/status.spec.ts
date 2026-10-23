import { mount } from '@vue/test-utils'
import HealthBadge from '@/components/common/HealthBadge.vue'
import PipelineStatusTag from '@/components/common/PipelineStatusTag.vue'
import StatTile from '@/components/common/StatTile.vue'
import { withPrimeVue } from './primevue'

describe('status components', () => {
  it.each([
    ['UP', 'Up', 'up'],
    ['DOWN', 'Down', 'down'],
    ['UNKNOWN', 'Unknown', 'unknown'],
  ] as const)('shows health %s with an icon and a label', (status, label, tone) => {
    const badge = mount(HealthBadge, { props: { status } })

    expect(badge.text()).toBe(label)
    expect(badge.classes()).toContain(tone)
    expect(badge.find('i.pi').exists()).toBe(true)
  })

  it('names a pipeline status in plain words', () => {
    const tag = mount(PipelineStatusTag, { props: { status: 'HALTED' }, ...withPrimeVue })

    expect(tag.text()).toBe('Halted')
  })

  it('shows a headline number with its context and trend', () => {
    const tile = mount(StatTile, {
      props: { label: 'Members ingested', value: '116.5K', icon: 'pi pi-download', caption: 'All servers', trend: [1, 4, 2, 8] },
    })

    expect(tile.find('.stat-label').text()).toBe('Members ingested')
    expect(tile.find('.stat-value').text()).toBe('116.5K')
    expect(tile.find('.stat-caption').text()).toBe('All servers')
    expect(tile.find('svg.sparkline path').attributes('d')).toMatch(/^M3\.0,/)
  })

  it('leaves out the sparkline without a trend', () => {
    const tile = mount(StatTile, { props: { label: 'Views', value: '3', icon: 'pi pi-th-large' } })

    expect(tile.find('svg.sparkline').exists()).toBe(false)
  })
})
