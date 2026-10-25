import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { usePolling } from '@/composables/useAsync'
import { MockLdesAdminApi } from '@/api/mock/MockLdesAdminApi'
import { IDS } from '@/api/mock/sampleData'
import { DEFAULT_PROCESS_REFRESH_SECONDS, useUiStore } from '@/stores/ui'
import { withPrimeVue } from '../components/primevue'

describe('refreshing the background processes', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  afterEach(() => jest.useRealTimers())

  it('refreshes every 2 seconds unless the user chose otherwise, and remembers the choice', async () => {
    const ui = useUiStore()
    expect(ui.processRefreshSeconds).toBe(DEFAULT_PROCESS_REFRESH_SECONDS)
    expect(DEFAULT_PROCESS_REFRESH_SECONDS).toBe(2)

    ui.processRefreshSeconds = 10
    await nextTick()

    setActivePinia(createPinia())
    expect(useUiStore().processRefreshSeconds).toBe(10)
  })

  it('loads again while active, every so many seconds, and stops when it unmounts', async () => {
    jest.useFakeTimers()
    const load = jest.fn(async () => undefined)
    const seconds = ref(2)
    const active = ref(true)
    const wrapper = mount(defineComponent({
      setup() {
        usePolling(load, () => seconds.value, () => active.value)
        return () => h('div')
      },
    }), withPrimeVue)

    await jest.advanceTimersByTimeAsync(4100)
    expect(load).toHaveBeenCalledTimes(2)

    active.value = false
    await nextTick()
    await jest.advanceTimersByTimeAsync(6000)
    expect(load).toHaveBeenCalledTimes(2)

    active.value = true
    seconds.value = 0
    await nextTick()
    await jest.advanceTimersByTimeAsync(6000)
    expect(load).toHaveBeenCalledTimes(2)

    seconds.value = 1
    await nextTick()
    await jest.advanceTimersByTimeAsync(1000)
    expect(load).toHaveBeenCalledTimes(3)

    wrapper.unmount()
    await jest.advanceTimersByTimeAsync(5000)
    expect(load).toHaveBeenCalledTimes(3)
  })

  it('has sample servers whose fragmentation job keeps running', async () => {
    let now = new Date('2026-10-25T13:00:00Z')
    const api = new MockLdesAdminApi({ latency: 0, now: () => now })
    const runs = async () => (await api.getBackgroundProcesses(IDS.production))
      .map((process) => [process.step, process.completedRuns])

    const before = await runs()
    now = new Date(now.getTime() + 12_000)
    const after = await runs()

    expect(after.find(([step]) => step === 'pagination')![1])
      .toBe(Number(before.find(([step]) => step === 'pagination')![1]) + 2)
    expect(after.find(([step]) => step === 'compactionStep')).toEqual(before.find(([step]) => step === 'compactionStep'))
  })
})
