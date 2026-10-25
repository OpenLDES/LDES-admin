import { createPinia, setActivePinia } from 'pinia'
import { api, apiMode } from '@/api'
import { initialMode, useModeStore } from '@/stores/mode'

function backend(up: boolean) {
  globalThis.fetch = jest.fn(async () => up
    ? { ok: true, status: 200, json: async () => ({ status: 'UP' }) }
    : Promise.reject(new TypeError('Failed to fetch'))) as unknown as typeof fetch
}

describe('mode', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('starts in production mode when the backend answers', async () => {
    backend(true)

    expect(await initialMode()).toEqual({ mode: 'production', backendUp: true })
  })

  it('starts in demo mode when the backend does not answer', async () => {
    backend(false)

    expect(await initialMode()).toEqual({ mode: 'demo', backendUp: false })
  })

  it('remembers the mode the user chose', async () => {
    backend(true)
    const mode = useModeStore()
    mode.start('production', true)

    await mode.switchTo('demo')

    expect(localStorage.getItem('ldes-admin.mode')).toBe('demo')
    expect(await initialMode()).toEqual({ mode: 'demo', backendUp: undefined })
  })

  it('switches the API and asks the views to start afresh', async () => {
    backend(false)
    const mode = useModeStore()
    mode.start('demo', undefined)

    await mode.switchTo('production')

    expect(apiMode()).toBe('production')
    expect(mode.generation).toBe(1)
    expect(mode.backendUnreachable).toBe(true)
    await expect(api.listInstances('LDIO')).rejects.toMatchObject({ status: 0 })

    await mode.switchTo('demo')
    expect((await api.listInstances('LDIO')).length).toBeGreaterThan(0)
  })
})
