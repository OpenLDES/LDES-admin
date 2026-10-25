import { HttpLdesAdminApi } from './http/HttpLdesAdminApi'
import { MockLdesAdminApi } from './mock/MockLdesAdminApi'
import type { LdesAdminApi } from './LdesAdminApi'

export { ApiError } from './LdesAdminApi'
export type { LdesAdminApi } from './LdesAdminApi'

/**
 * demo: the API runs on in-memory sample data, to explore LDES-admin without servers.
 * production: the API talks to the LDES-admin backend, which manages real LDES servers and LDIOs.
 */
export type ApiMode = 'demo' | 'production'

const implementations: Record<ApiMode, LdesAdminApi> = {
  demo: new MockLdesAdminApi(),
  production: new HttpLdesAdminApi(),
}

let currentMode: ApiMode = 'demo'

export function apiMode(): ApiMode {
  return currentMode
}

/** Switches the implementation behind {@link api}; use the mode store to switch the whole app */
export function setApiMode(mode: ApiMode): void {
  currentMode = mode
}

/**
 * The API the frontend talks to: sample data or the backend, depending on the mode. Components
 * keep this one reference; it forwards every call to the implementation of the current mode.
 */
export const api: LdesAdminApi = new Proxy({} as LdesAdminApi, {
  get(_, property) {
    const implementation = implementations[currentMode]
    const value = Reflect.get(implementation, property, implementation)
    return typeof value === 'function' ? value.bind(implementation) : value
  },
})

/** Whether the LDES-admin backend answers, within the given time */
export async function backendAvailable(timeoutMs = 2000): Promise<boolean> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch('/actuator/health', {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    })
    if (!response.ok) return false
    const health = await response.json() as { status?: string }
    return health.status === 'UP'
  } catch {
    return false
  } finally {
    clearTimeout(timer)
  }
}
