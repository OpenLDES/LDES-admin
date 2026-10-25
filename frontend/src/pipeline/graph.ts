import type { ComponentConfig, ComponentDefinition, Pipeline } from '@/domain/ldio'
import { componentInfo, type ComponentKind, kinds } from './catalog'
import { configProblems } from './config'

/**
 * A pipeline drawn as a graph: one node per component, edges in the direction the data flows.
 * The shapes are compatible with Vue Flow nodes and edges, without depending on Vue Flow.
 *
 * <pre>
 * input ──▶ adapter ──▶ transformer ──▶ … ──▶ transformer ──┬──▶ output
 *                                                             └──▶ output
 * </pre>
 */
export type NodeRunStatus = 'idle' | 'active' | 'done' | 'filtered' | 'error' | 'skipped'

export interface PipelineNodeData {
  component: string
  kind: ComponentKind
  config: ComponentConfig
  runStatus?: NodeRunStatus
  /** Decides where the ports of the node are */
  direction?: LayoutDirection
}

export interface GraphNode {
  id: string
  type: 'ldio'
  position: { x: number; y: number }
  data: PipelineNodeData
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  animated?: boolean
  class?: string
}

export interface GraphIssue {
  severity: 'error' | 'warning'
  message: string
  nodeId?: string
  /** For a problem with the configuration of a component: the property it is about */
  property?: string
}

export interface GraphConversion {
  /** As much of the pipeline as could be derived; complete when there are no errors */
  pipeline?: Pipeline
  issues: GraphIssue[]
  /** No errors at all: the pipeline can be deployed */
  valid: boolean
  /** No errors in the structure of the pipeline: it can be simulated, whatever its configuration */
  runnable: boolean
  /** Node ids in pipeline order: input, adapter, transformers, outputs */
  order: string[]
}

export const PIPELINE_NAME = /^[A-Za-z0-9][A-Za-z0-9_-]*$/

/** Top to bottom fits the sandbox canvas best; left to right fits wide previews */
export type LayoutDirection = 'TB' | 'LR'

/** Distance between consecutive components, and between outputs side by side */
const SPACING: Record<LayoutDirection, { along: number; across: number }> = {
  TB: { along: 110, across: 240 },
  LR: { along: 260, across: 120 },
}

let counter = 0

export function nextNodeId(): string {
  counter += 1
  return `node-${Date.now().toString(36)}-${counter}`
}

export function edgeId(source: string, target: string): string {
  return `edge-${source}-${target}`
}

export function createNode(
  component: string,
  position: { x: number; y: number },
  config?: ComponentConfig,
  id: string = nextNodeId(),
  direction: LayoutDirection = 'TB',
): GraphNode {
  const info = componentInfo(component)
  if (!info) {
    throw new Error(`Unknown component ${component}`)
  }
  return {
    id,
    type: 'ldio',
    position,
    data: {
      component,
      kind: info.kind,
      config: cloneConfig(config ?? info.defaultConfig),
      direction,
    },
  }
}

/**
 * Draws a pipeline as a chain of components, with the outputs side by side at the end.
 */
export function pipelineToGraph(pipeline: Pipeline, direction: LayoutDirection = 'TB'):
  { nodes: GraphNode[]; edges: GraphEdge[] } {
  const chain: { id: string; definition: ComponentDefinition }[] = [
    { id: 'input', definition: pipeline.input },
  ]
  if (pipeline.input.adapter) {
    chain.push({ id: 'adapter', definition: pipeline.input.adapter })
  }
  ;(pipeline.transformers ?? []).forEach((definition, index) =>
    chain.push({ id: `transformer-${index + 1}`, definition }),
  )
  const outputs = (pipeline.outputs ?? []).map((definition, index) => ({
    id: `output-${index + 1}`,
    definition,
  }))

  const positions = chainPositions(chain.map(({ id }) => id), outputs.map(({ id }) => id), direction)
  const nodes = [...chain, ...outputs].map(({ id, definition }) =>
    createNode(definition.name, positions.get(id)!, configOf(definition), id, direction))

  const edges: GraphEdge[] = []
  for (let i = 1; i < chain.length; i++) {
    edges.push(edge(chain[i - 1].id, chain[i].id))
  }
  const last = chain[chain.length - 1].id
  outputs.forEach(({ id }) => edges.push(edge(last, id)))
  return { nodes, edges }
}

function chainPositions(chain: string[], outputs: string[], direction: LayoutDirection):
  Map<string, { x: number; y: number }> {
  const { along, across } = SPACING[direction]
  const place = (step: number, offset: number) => direction === 'LR'
    ? { x: step * along, y: offset * across }
    : { x: offset * across, y: step * along }
  const positions = new Map<string, { x: number; y: number }>()
  chain.forEach((id, step) => positions.set(id, place(step, 0)))
  outputs.forEach((id, index) => positions.set(id, place(chain.length, index - (outputs.length - 1) / 2)))
  return positions
}

/**
 * Derives the pipeline definition from the graph, and reports everything that keeps it from being
 * a valid LDIO pipeline.
 */
export function graphToPipeline(
  meta: { name: string; description?: string },
  nodes: GraphNode[],
  edges: GraphEdge[],
): GraphConversion {
  const issues: GraphIssue[] = []
  const error = (message: string, nodeId?: string) =>
    issues.push({ severity: 'error', message, nodeId })
  const warning = (message: string, nodeId?: string) =>
    issues.push({ severity: 'warning', message, nodeId })
  const byId = new Map(nodes.map((node) => [node.id, node]))
  const next = (node: GraphNode) =>
    edges
      .filter((e) => e.source === node.id)
      .map((e) => byId.get(e.target))
      .filter((target): target is GraphNode => target !== undefined)

  if (!PIPELINE_NAME.test(meta.name)) {
    error("The pipeline name may only contain letters, digits, '-' and '_'")
  }
  nodes.forEach((node) =>
    configProblems(node.data.component, node.data.config).forEach((problem) => {
      const where = problem.index === undefined ? problem.key : `${problem.key} (line ${problem.index + 1})`
      const message = problem.message === 'Required'
        ? `${label(node)} needs '${problem.key}'`
        : `${label(node)}${where ? ` – ${where}` : ''}: ${problem.message}`
      issues.push({ severity: problem.severity, message, nodeId: node.id, property: problem.key ?? '' })
    }),
  )

  const inputs = nodes.filter((node) => node.data.kind === 'input')
  if (inputs.length === 0) {
    error('Add an input: every pipeline starts with one')
    return { issues, valid: false, runnable: false, order: [] }
  }
  inputs.slice(1).forEach((extra) => error('A pipeline has exactly one input', extra.id))

  const input = inputs[0]
  const order = [input.id]
  const afterInput = next(input)
  const adapters = afterInput.filter((node) => node.data.kind === 'adapter')
  let adapter: GraphNode | undefined
  let cursor = input
  if (adapters.length > 1) {
    error('An input has at most one adapter', input.id)
  }
  if (adapters.length >= 1) {
    adapter = adapters[0]
    order.push(adapter.id)
    cursor = adapter
    if (afterInput.length > 1) {
      error(`Continue the pipeline from the adapter, not from ${label(input)}`, input.id)
    }
  }
  const requiresAdapter = componentInfo(input.data.component)?.requiresAdapter ?? false
  if (requiresAdapter && !adapter) {
    error(`${label(input)} receives raw data and needs an adapter`, input.id)
  }
  if (!requiresAdapter && adapter) {
    warning(`${label(input)} produces linked data itself; it does not use an adapter`, adapter.id)
  }

  const transformers: GraphNode[] = []
  let outputs: GraphNode[] = []
  for (;;) {
    const following = next(cursor)
    const nextTransformers = following.filter((node) => node.data.kind === 'transformer')
    outputs = following.filter((node) => node.data.kind === 'output')
    if (nextTransformers.length > 1) {
      error(`Transformers form a single chain, but ${label(cursor)} has several`, cursor.id)
    }
    if (nextTransformers.length > 0 && outputs.length > 0) {
      error(`Outputs come after the last transformer, not after ${label(cursor)}`, cursor.id)
    }
    const transformer = nextTransformers[0]
    if (!transformer || order.includes(transformer.id)) {
      break
    }
    transformers.push(transformer)
    order.push(transformer.id)
    cursor = transformer
  }
  if (outputs.length === 0) {
    error('Add an output and connect it after the last component')
  }
  order.push(...outputs.map((output) => output.id))

  nodes
    .filter((node) => !order.includes(node.id) && !inputs.slice(1).includes(node))
    .forEach((node) => warning(`${label(node)} is not connected to the pipeline`, node.id))

  const pipeline: Pipeline = {
    name: meta.name,
    description: meta.description || undefined,
    input: {
      ...definitionOf(input),
      ...(adapter ? { adapter: definitionOf(adapter) } : {}),
    },
    transformers: transformers.map(definitionOf),
    outputs: outputs.map(definitionOf),
  }
  return {
    pipeline,
    issues,
    valid: issues.every((issue) => issue.severity !== 'error'),
    runnable: issues.every((issue) => issue.severity !== 'error' || issue.property !== undefined),
    order,
  }
}

/**
 * Why a connection from source to target is not allowed, or undefined when it is.
 */
export function connectionProblem(
  source: GraphNode | undefined,
  target: GraphNode | undefined,
  edges: GraphEdge[],
): string | undefined {
  if (!source || !target) return 'Unknown component'
  if (source.id === target.id) return 'A component cannot connect to itself'
  if (source.data.kind === 'output') return 'An output ends the pipeline'
  if (target.data.kind === 'input') return 'An input starts the pipeline'
  if (target.data.kind === 'adapter' && source.data.kind !== 'input') {
    return 'An adapter can only follow an input'
  }
  if (edges.some((e) => e.source === source.id && e.target === target.id)) {
    return 'These components are already connected'
  }
  if (edges.some((e) => e.target === target.id)) {
    return `${label(target)} already has an incoming connection`
  }
  if (createsCycle(source.id, target.id, edges)) return 'Pipelines cannot loop'
  return undefined
}

function createsCycle(source: string, target: string, edges: GraphEdge[]): boolean {
  const toVisit = [target]
  const visited = new Set<string>()
  while (toVisit.length > 0) {
    const current = toVisit.pop()!
    if (current === source) return true
    if (visited.has(current)) continue
    visited.add(current)
    edges.filter((e) => e.source === current).forEach((e) => toVisit.push(e.target))
  }
  return false
}

/**
 * Arranges connected nodes left to right in pipeline order; unconnected nodes go below.
 */
export function autoLayout(
  nodes: GraphNode[],
  conversion: Pick<GraphConversion, 'order'>,
  direction: LayoutDirection = 'TB',
): GraphNode[] {
  const outputs = conversion.order.filter((id) => nodes.find((n) => n.id === id)?.data.kind === 'output')
  const chain = conversion.order.filter((id) => !outputs.includes(id))
  const positions = chainPositions(chain, outputs, direction)
  // Components that are not part of the pipeline line up beside it
  const { along, across } = SPACING[direction]
  const aside = (Math.max(1, outputs.length) / 2 + 1) * across
  let loose = 0
  return nodes.map((node) => {
    let position = positions.get(node.id)
    if (!position) {
      const step = loose++
      position = direction === 'LR' ? { x: step * along, y: aside } : { x: aside, y: step * along }
    }
    return { ...node, position }
  })
}

export function label(node: GraphNode): string {
  return componentInfo(node.data.component)?.label ?? node.data.component
}

export function kindLabel(kind: ComponentKind): string {
  return kinds[kind].label
}

function edge(source: string, target: string): GraphEdge {
  return { id: edgeId(source, target), source, target }
}

function configOf(definition: ComponentDefinition): ComponentConfig {
  return definition.config ?? {}
}

function definitionOf(node: GraphNode): ComponentDefinition {
  return Object.keys(node.data.config).length > 0
    ? { name: node.data.component, config: cloneConfig(node.data.config) }
    : { name: node.data.component }
}

/**
 * Config is plain JSON; unlike structuredClone this also copies Vue's reactive proxies.
 */
export function cloneConfig(config: ComponentConfig): ComponentConfig {
  return JSON.parse(JSON.stringify(config))
}
