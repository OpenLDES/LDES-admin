/**
 * LDES servers and LDIOs managed by LDES-admin. Mirrors the instance web models of the backend
 * (adapter/in/rest/instance).
 */
export type InstanceType = 'LDES_SERVER' | 'LDIO'

/** REGISTERED: added by its URL. LOCAL: started (and stopped) by LDES-admin as a container. */
export type InstanceOrigin = 'REGISTERED' | 'LOCAL'

export type HealthStatus = 'UP' | 'DOWN' | 'UNKNOWN'

export interface Instance {
  id: string
  type: InstanceType
  name: string
  url: string
  origin: InstanceOrigin
  sandbox: boolean
  registeredAt: string
}

export interface InstanceHealth {
  status: HealthStatus
  checkedAt: string
}

export interface RegisterInstanceRequest {
  name: string
  url: string
  sandbox?: boolean
}

export interface StartLocalInstanceRequest {
  name: string
  sandbox?: boolean
}

export const instanceTypeLabel: Record<InstanceType, string> = {
  LDES_SERVER: 'LDES server',
  LDIO: 'LDIO',
}
