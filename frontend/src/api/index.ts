import { MockLdesAdminApi } from './mock/MockLdesAdminApi'
import type { LdesAdminApi } from './LdesAdminApi'

export { ApiError } from './LdesAdminApi'
export type { LdesAdminApi } from './LdesAdminApi'

/**
 * The API the frontend talks to. It runs on sample data for now; replace this with an HTTP
 * implementation of LdesAdminApi to talk to the backend (the Vite dev server proxies /api).
 */
export const api: LdesAdminApi = new MockLdesAdminApi()
