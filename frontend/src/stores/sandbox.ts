import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import type { ComponentConfig, Pipeline } from '@/domain/ldio'
import type { SimulationInput, SimulationResult } from '@/domain/sandbox'
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
import { describeSchedule, nextPoll, type Poller, pollerOf } from '@/pipeline/poller'
import { defaultTemplate, templateById } from '@/pipeline/templates'
import { parsePipelineYaml, pipelineToYaml } from '@/pipeline/yaml'

/** Where the pipeline in the sandbox came from: deploying updates it in place */
export type PipelineOrigin =
  | { kind: 'template'; id: string }
  | { kind: 'ldio'; ldioId: string; ldioName: string }
  | { kind: 'scratch' }

/** A poll of the URL of the HTTP In Poller, for the input of the simulation */
export interface PollOutcome {
  at: Date
  url: string
  /** Why it failed; undefined when it succeeded */
  error?: string
}

const STEP_DELAY = 550
/** The longest delay setTimeout handles; a cron expression can fire later than that */
const MAX_TIMEOUT = 2 ** 31 - 1

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
  /** Why the last run could not run at all, e.g. the backend does not answer */
  const runError = ref<string>()
  const activeStep = ref(-1)
  /** Whether outputs really send what they make; a dry run by default */
  const executeOutputs = ref(false)
  /** Renewed with every pipeline loaded: the change detection filter forgets what it saw then */
  const session = ref(newSession())
  let currentRun: Promise<void> = Promise.resolve()

  const polling = ref(false)
  /** Successful polls since polling started */
  const polls = ref(0)
  const nextPollAt = ref<Date>()
  const lastPoll = ref<PollOutcome>()
  let pollTimer: ReturnType<typeof setTimeout> | undefined
  /** Changes when polling stops or starts again, so a poll of before knows to stop */
  let pollSession = 0
  let pollInProgress = false
  /** What the poller requested when the next poll was scheduled */
  let scheduledFor = ''

  const conversion = computed(() =>
    graphToPipeline({ name: name.value, description: description.value }, nodes.value, edges.value))
  const yaml = computed(() => conversion.value.pipeline ? pipelineToYaml(conversion.value.pipeline) : '')
  const selected = computed(() => nodes.value.find((node) => node.id === selectedId.value))
  const errors = computed(() => conversion.value.issues.filter((issue) => issue.severity === 'error'))
  const warnings = computed(() => conversion.value.issues.filter((issue) => issue.severity === 'warning'))
  /** The HTTP In Poller the pipeline starts with, if it does */
  const poller = computed(() => pollerOf(conversion.value.pipeline))

  function issuesOf(nodeId: string): GraphIssue[] {
    return conversion.value.issues.filter((issue) => issue.nodeId === nodeId)
  }

  function load(pipeline: Pipeline, input?: SimulationInput, from: PipelineOrigin = { kind: 'scratch' }) {
    stopPolling()
    lastPoll.value = undefined
    const graph = pipelineToGraph(pipeline)
    nodes.value = graph.nodes
    edges.value = graph.edges
    name.value = pipeline.name
    description.value = pipeline.description ?? ''
    if (input) sample.value = { ...input }
    origin.value = from
    selectedId.value = undefined
    resetSimulation()
    session.value = newSession()
  }

  function loadTemplate(id: string) {
    const template = templateById(id) ?? defaultTemplate
    load(template.pipeline, template.sample, { kind: 'template', id: template.id })
  }

  function clear() {
    stopPolling()
    lastPoll.value = undefined
    nodes.value = []
    edges.value = []
    name.value = 'my-pipeline'
    description.value = ''
    origin.value = { kind: 'scratch' }
    selectedId.value = undefined
    resetSimulation()
    session.value = newSession()
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

  /** Runs the input through the pipeline; while a run is in progress, answers that one */
  function run(): Promise<void> {
    if (!running.value) currentRun = simulate()
    return currentRun
  }

  async function simulate() {
    const pipeline = conversion.value.pipeline
    if (!pipeline || !conversion.value.runnable || running.value) return
    resetSimulation()
    running.value = true
    let outcome: SimulationResult
    try {
      outcome = await api.runPipeline({
        pipeline,
        input: { ...sample.value },
        session: session.value,
        executeOutputs: executeOutputs.value,
      })
    } catch (e) {
      runError.value = errorMessage(e)
      running.value = false
      return
    }
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

  // Polling: the input as the HTTP In Poller of the pipeline receives it -----------------------

  /** Puts what a URL answers in the input, as the poller would receive it */
  async function fetchInput(url = poller.value?.urls[0]): Promise<void> {
    const target = poller.value
    if (!target || !url) return
    const at = new Date()
    try {
      const content = await api.poll({ url, ...target.request })
      sample.value = { content: content.body, contentType: mediaType(content.contentType), source: url }
      lastPoll.value = { at, url }
    } catch (e) {
      lastPoll.value = { at, url, error: errorMessage(e) }
      throw e
    }
  }

  /** Polls on the schedule of the component, and runs every response through the pipeline */
  function startPolling() {
    const schedule = poller.value?.schedule
    if (!schedule || polling.value) return
    polling.value = true
    polls.value = 0
    pollSession++
    // Like LDIO: an interval polls right away, a cron expression waits until it fires
    schedulePoll(schedule.kind === 'interval')
  }

  function stopPolling() {
    polling.value = false
    pollSession++
    nextPollAt.value = undefined
    clearTimeout(pollTimer)
  }

  function schedulePoll(now = false) {
    clearTimeout(pollTimer)
    const schedule = poller.value?.schedule
    const at = schedule && (now ? new Date() : nextPoll(schedule, new Date()))
    if (!polling.value || !at) {
      stopPolling()
      return
    }
    scheduledFor = pollerKey(poller.value)
    nextPollAt.value = at
    const session = pollSession
    wakeAt(at, () => pollEveryUrl(session))
  }

  function wakeAt(at: Date, then: () => void) {
    const delay = at.getTime() - Date.now()
    pollTimer = delay > MAX_TIMEOUT
      ? setTimeout(() => wakeAt(at, then), MAX_TIMEOUT)
      : setTimeout(then, Math.max(delay, 0))
  }

  /** Like LDIO: every URL in turn, going on with the next one when a poll fails */
  async function pollEveryUrl(session: number) {
    if (session !== pollSession) return
    nextPollAt.value = undefined
    pollInProgress = true
    try {
      for (const url of poller.value?.urls ?? []) {
        // The next poll waits for the run before it, as the steps are shown one by one
        await currentRun
        if (session !== pollSession) return
        // The pipeline changed meanwhile: scheduling again stops polling
        if (!poller.value) break
        try {
          await fetchInput(url)
        } catch {
          continue
        }
        if (session !== pollSession) return
        polls.value++
        await run()
      }
    } finally {
      pollInProgress = false
    }
    if (session === pollSession) schedulePoll()
  }

  // A changed schedule or URL counts from now on; without a schedule, polling stops. Watchers run
  // later, so it compares with what the current schedule was made for.
  watch(() => pollerKey(poller.value), (key) => {
    if (polling.value && !pollInProgress && key !== scheduledFor) schedulePoll()
  })

  function resetSimulation() {
    if (running.value) return
    result.value = undefined
    runError.value = undefined
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
    runError,
    activeStep,
    executeOutputs,
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
    poller,
    polling,
    polls,
    nextPollAt,
    lastPoll,
    fetchInput,
    startPolling,
    stopPolling,
  }
})

/** Random enough to tell the pipelines of one browser apart; randomUUID needs a secure context */
function newSession(): string {
  return globalThis.crypto?.randomUUID?.()
    ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

/** Changes when what the poller requests, or when, changes */
function pollerKey(poller: Poller | undefined): string {
  return poller
    ? JSON.stringify([poller.urls, poller.request, poller.schedule && describeSchedule(poller.schedule)])
    : ''
}

/** application/json; charset=utf-8 becomes application/json */
function mediaType(contentType: string): string {
  return contentType.split(';')[0].trim().toLowerCase()
}
