import { createPinia, setActivePinia } from 'pinia'
import type { Instance, InstanceType } from '@/domain/instance'
import { useInstancesStore } from '@/stores/instances'

const mockListInstances = jest.fn()
jest.mock('@/api', () => ({
  ...jest.requireActual('@/api'),
  api: { listInstances: (...args: unknown[]) => mockListInstances(...args) },
}))

function server(name: string): Instance {
  return {
    id: name,
    type: 'LDES_SERVER',
    name,
    url: `https://${name}.example.org`,
    origin: 'REGISTERED',
    sandbox: false,
    registeredAt: '2026-10-26T10:00:00Z',
  } as Instance
}

describe('instances store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockListInstances.mockReset()
  })

  it('keeps the latest load when an earlier one answers later, as after a switch of mode', async () => {
    let answerDemo: (servers: Instance[]) => void = () => undefined
    mockListInstances
      .mockImplementationOnce(() => new Promise((resolve) => {
        answerDemo = resolve
      }))
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([server('production')])
      .mockResolvedValueOnce([])
    const instances = useInstancesStore()

    const demo = instances.load()
    await instances.load()
    answerDemo([server('demo')])
    await demo

    expect(instances.ldesServers.map((instance) => instance.name)).toEqual(['production'])
    expect(instances.loading).toBe(false)
    expect(mockListInstances.mock.calls.map(([type]) => type as InstanceType))
      .toEqual(['LDES_SERVER', 'LDIO', 'LDES_SERVER', 'LDIO'])
  })
})
