import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import { useModeStore } from '@/stores/mode'
import { withPrimeVue } from './primevue'

const mockToast = { add: jest.fn() }

jest.mock('primevue/usetoast', () => ({ useToast: () => mockToast }))
jest.mock('vue-router', () => ({
  ...jest.requireActual('vue-router'),
  useRoute: () => ({ name: 'dashboard', params: {}, meta: {} }),
}))

describe('top bar', () => {
  const build = globalThis as { __DEMO_ONLY__?: boolean }

  beforeEach(() => {
    mockToast.add.mockClear()
    localStorage.clear()
  })

  afterEach(() => (build.__DEMO_ONLY__ = false))

  function mountTopbar() {
    setActivePinia(createPinia())
    const mode = useModeStore()
    mode.start('demo', undefined)
    const topbar = mount(AppTopbar, {
      global: { ...withPrimeVue.global, stubs: { RouterLink: true } },
    })
    return { mode, topbar }
  }

  it('refuses production mode in a demo-only build, and says why', async () => {
    build.__DEMO_ONLY__ = true
    const { mode, topbar } = mountTopbar()

    await topbar.get('[data-cy=mode-production]').trigger('click')

    expect(mode.mode).toBe('demo')
    expect(topbar.get('[data-cy=mode-demo]').attributes('aria-checked')).toBe('true')
    expect(mockToast.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      detail: expect.stringContaining('only for demo purposes'),
    }))
    expect(mockToast.add.mock.calls[0][0].detail).toContain('cannot interact with LDES servers and LDIOs')
    expect(mockToast.add.mock.calls[0][0].detail).toContain('production mode')
  })

  it('switches to production mode in a regular build', async () => {
    globalThis.fetch = jest.fn(async () => ({ ok: true, json: async () => ({ status: 'UP' }) })) as unknown as typeof fetch
    const { mode, topbar } = mountTopbar()

    await topbar.get('[data-cy=mode-production]').trigger('click')

    expect(mode.mode).toBe('production')
    expect(mockToast.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }))
  })
})
