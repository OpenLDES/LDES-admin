import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { ComponentConfig, Pipeline } from '@/domain/ldio'
import {
  autoLayout,
  connectionProblem,
  createNode,
  edgeId,
  type GraphEdge,
  type GraphNode,
  type GraphIssue,
  graphToPipeline,
  type NodeRunStatus,
  pipelineToGraph,
} from '@/pipeline/graph'
import { PipelineSimulator, type SimulationInput, type SimulationResult } from '@/pipeline/simulator'
import { defaultTemplate, templateById } from '@/pipeline/templates'
import { parsePipelineYaml, pipelineToYaml } from '@/pipeline/yaml'

/** Where the pipeline in the sandbox came from: deploying updates it in place */
export type PipelineOrigin =
  | { kind: 'template'; id: string }
  | { kind: 'ldio'; ldioId: string; ldioName: string }
  | { kind: 'scratch' }

const STEP_DELAY = 550

/**
 * The pipeline being edited in the sandbox, as a graph, and its simulation. Lives in a store so it
 * survives navigating away from the sandbox.
 */
export const useSandboxStore = defineStore('sandbox', () => {
  const name = ref('')
  const description = ref('')
  const nodes = ref<GraphNode[]>([])
  const edges = ref<GraphEdge[]>([])
  const selectedId = ref<string>()
  const sample = ref<SimulationInput>({ content: '', contentType: 'application/json' })
  const origin = ref<PipelineOrigin>({ kind: 'scratch' })

  const running = ref(false)
  const result = ref<SimulationResult>()
  const activeStep = ref(-1)
  const simulator = new PipelineSimulator()

  const conversion = computed(() =>
    graphToPipeline({ name: name.value, description: description.value }, nodes.value, edges.value))
  const yaml = computed(() => conversion.value.pipeline ? pipelineToYaml(conversion.value.pipeline) : '')
  const selected = computed(() => nodes.value.find((node) => node.id === selectedId.value))
  const errors = computed(() => conversion.value.issues.filter((issue) => issue.severity === 'error'))
  const warnings = computed(() => conversion.value.issues.filter((issue) => issue.severity === 'warning'))

  function issuesOf(nodeId: string): GraphIssue[] {
    return conversion.value.issues.filter((issue) => issue.nodeId === nodeId)
  }

  function load(pipeline: Pipeline, input?: SimulationInput, from: PipelineOrigin = { kind: 'scratch' }) {
    const graph = pipelineToGraph(pipeline)
    nodes.value = graph.nodes
    edges.value = graph.edges
    name.value = pipeline.name
    description.value = pipeline.description ?? ''
    if (input) sample.value = { ...input }
    origin.value = from
    selectedId.value = undefined
    resetSimulation()
    simulator.reset()
  }

  function loadTemplate(id: string) {
    const template = templateById(id) ?? defaultTemplate
    load(template.pipeline, template.sample, { kind: 'template', id: template.id })
  }

  function clear() {
    nodes.value = []
    edges.value = []
    name.value = 'my-pipeline'
    description.value = ''
    origin.value = { kind: 'scratch' }
    selectedId.value = undefined
    resetSimulation()
  }

  function ensureInitialised() {
    if (nodes.value.length === 0 && name.value === '') loadTemplate(defaultTemplate.id)
  }

  /** Below the lowest component, so a new one never lands on top of another */
  function nextPosition(): { x: number; y: number } {
    if (nodes.value.length === 0) return { x: 0, y: 0 }
    const bottom = Math.max(...nodes.value.map((node) => node.position.y))
    return { x: 0, y: bottom + 110 }
  }

  function addComponent(component: string, position = nextPosition()): GraphNode {
    const node = createNode(component, position)
    nodes.value = [...nodes.value, node]
    selectedId.value = node.id
    resetSimulation()
    return node
  }

  /** Connects two nodes, or explains why that is not possible */
  function connect(source: string, target: string): string | undefined {
    const problem = connectionProblem(find(source), find(target), edges.value)
    if (!problem) {
      edges.value = [...edges.value, { id: edgeId(source, target), source, target }]
      resetSimulation()
    }
    return problem
  }

  function removeNode(id: string) {
    nodes.value = nodes.value.filter((node) => node.id !== id)
    edges.value = edges.value.filter((edge) => edge.source !== id && edge.target !== id)
    if (selectedId.value === id) selectedId.value = undefined
    resetSimulation()
  }

  function duplicate(id: string) {
    const node = find(id)
    if (!node) return
    const copy = createNode(node.data.component, { x: node.position.x + 240, y: node.position.y },
      node.data.config)
    nodes.value = [...nodes.value, copy]
    selectedId.value = copy.id
  }

  function updateComponent(id: string, component: string, config: ComponentConfig) {
    nodes.value = nodes.value.map((node) => node.id === id
      ? { ...node, data: { ...node.data, component, config } }
      : node)
    resetSimulation()
  }

  function layout() {
    nodes.value = autoLayout(nodes.value, conversion.value)
  }

  /** Replaces the graph with the pipeline in the YAML, or returns why it can't */
  function applyYaml(text: string): string | undefined {
    const parsed = parsePipelineYaml(text)
    if (!parsed.ok) return parsed.error
    load(parsed.pipeline, undefined, origin.value)
    return undefined
  }

  async function run() {
    const pipeline = conversion.value.pipeline
    if (!pipeline || !conversion.value.valid || running.value) return
    resetSimulation()
    running.value = true
    const outcome = simulator.run(pipeline, sample.value)
    const ids = nodeIdsInOrder()
    for (const [index, step] of outcome.steps.entries()) {
      activeStep.value = index
      const id = ids[step.kind][step.index]
      setRunStatus(id, 'active')
      flowInto(id)
      await new Promise((resolve) => setTimeout(resolve, STEP_DELAY))
      setRunStatus(id, step.status === 'ok' ? 'done' : step.status)
      result.value = { ...outcome, steps: outcome.steps.slice(0, index + 1) }
    }
    const reached = new Set(outcome.steps.map((step) => ids[step.kind][step.index]))
    nodes.value = nodes.value.map((node) => conversion.value.order.includes(node.id) && !reached.has(node.id)
      ? { ...node, data: { ...node.data, runStatus: 'skipped' } } : node)
    result.value = outcome
    edges.value = edges.value.map((edge) => ({ ...edge, animated: false }))
    running.value = false
  }

  function resetSimulation() {
    if (running.value) return
    result.value = undefined
    activeStep.value = -1
    if (nodes.value.some((node) => node.data.runStatus)) {
      nodes.value = nodes.value.map((node) => ({ ...node, data: { ...node.data, runStatus: undefined } }))
    }
    if (edges.value.some((edge) => edge.animated || edge.class)) {
      edges.value = edges.value.map((edge) => ({ ...edge, animated: false, class: undefined }))
    }
  }

  function find(id: string) {
    return nodes.value.find((node) => node.id === id)
  }

  function nodeIdsInOrder(): Record<GraphNode['data']['kind'], string[]> {
    const ofKind = (kind: GraphNode['data']['kind']) =>
      conversion.value.order.filter((id) => find(id)?.data.kind === kind)
    return {
      input: ofKind('input'),
      adapter: ofKind('adapter'),
      transformer: ofKind('transformer'),
      output: ofKind('output'),
    }
  }

  /** The LDIO the pipeline was deployed on belongs to another mode: keep the pipeline, not the link */
  function forgetDeployment() {
    if (origin.value.kind === 'ldio') origin.value = { kind: 'scratch' }
  }

  function setRunStatus(id: string, runStatus: NodeRunStatus) {
    nodes.value = nodes.value.map((node) => node.id === id
      ? { ...node, data: { ...node.data, runStatus } } : node)
  }

  function flowInto(id: string) {
    edges.value = edges.value.map((edge) => edge.target === id
      ? { ...edge, animated: true, class: 'flowing' } : edge)
  }

  return {
    name,
    description,
    nodes,
    edges,
    selectedId,
    sample,
    origin,
    running,
    result,
    activeStep,
    conversion,
    yaml,
    selected,
    errors,
    warnings,
    issuesOf,
    load,
    loadTemplate,
    clear,
    ensureInitialised,
    addComponent,
    connect,
    removeNode,
    duplicate,
    updateComponent,
    layout,
    applyYaml,
    run,
    resetSimulation,
    forgetDeployment,
  }
})
