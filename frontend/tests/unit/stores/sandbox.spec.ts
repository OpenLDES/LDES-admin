import { createPinia, setActivePinia } from 'pinia'
import { useSandboxStore } from '@/stores/sandbox'

describe('sandbox store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts with the default template', () => {
    const sandbox = useSandboxStore()

    sandbox.ensureInitialised()

    expect(sandbox.name).toBe('parcels-to-ldes')
    expect(sandbox.conversion.valid).toBe(true)
    expect(sandbox.yaml).toContain('name: Ldio:VersionObjectCreator')
  })

  it('adds components and only accepts valid connections', () => {
    const sandbox = useSandboxStore()
    sandbox.clear()
    const input = sandbox.addComponent('Ldio:LdesClient')
    const output = sandbox.addComponent('Ldio:ConsoleOut')

    expect(sandbox.connect(output.id, input.id)).toBe('An output ends the pipeline')
    expect(sandbox.connect(input.id, output.id)).toBeUndefined()
    expect(sandbox.conversion.valid).toBe(true)
    expect(sandbox.selectedId).toBe(output.id)
  })

  it('places new components below the pipeline', () => {
    const sandbox = useSandboxStore()
    sandbox.loadTemplate('http-to-console')

    const added = sandbox.addComponent('Ldio:NoopOut')

    expect(added.position).toEqual({ x: 0, y: 330 })
  })

  it('applies an edited component and removes nodes with their edges', () => {
    const sandbox = useSandboxStore()
    sandbox.loadTemplate('http-to-console')

    sandbox.updateComponent('output-1', 'Ldio:HttpOut', { endpoint: 'http://localhost:8080/x' })
    expect(sandbox.conversion.pipeline?.outputs).toEqual([
      { name: 'Ldio:HttpOut', config: { endpoint: 'http://localhost:8080/x' } },
    ])

    sandbox.removeNode('adapter')
    expect(sandbox.edges).toHaveLength(0)
    expect(sandbox.errors.map((issue) => issue.message)).toContain('HTTP In receives raw data and needs an adapter')
  })

  it('redraws the canvas from YAML, or explains what is wrong', () => {
    const sandbox = useSandboxStore()

    expect(sandbox.applyYaml('name: [')).toBeDefined()
    expect(sandbox.applyYaml(`name: from-yaml
input:
  name: Ldio:LdesClient
  config:
    urls: [http://localhost:8080/parcels]
outputs:
  - name: Ldio:ConsoleOut`)).toBeUndefined()
    expect(sandbox.name).toBe('from-yaml')
    expect(sandbox.nodes).toHaveLength(2)
  })

  it('runs a member through the pipeline, one node at a time', async () => {
    jest.useFakeTimers()
    const sandbox = useSandboxStore()
    sandbox.loadTemplate('http-to-console')

    const running = sandbox.run()
    expect(sandbox.running).toBe(true)
    await jest.runAllTimersAsync()
    await running

    expect(sandbox.running).toBe(false)
    expect(sandbox.result?.delivered).toBe(true)
    expect(sandbox.result?.steps.map((step) => step.component))
      .toEqual(['Ldio:HttpIn', 'Ldio:RdfAdapter', 'Ldio:ConsoleOut'])
    expect(sandbox.nodes.every((node) => node.data.runStatus === 'done')).toBe(true)
    jest.useRealTimers()
  })
})
