import { api as defaultApi, type LdesAdminApi } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import type { Instance } from '@/domain/instance'
import type { ComponentDefinition, Pipeline } from '@/domain/ldio'
import { type DemoExample, examplePipelineName, exampleViews } from './example'

/**
 * Creates an example on an LDES server and an LDIO, step by step: the event stream, its views, the
 * pipeline that feeds it and the sample data. What exists already is left as it is, so creating an
 * example twice does no harm.
 */
export interface InstallRequest {
  example: DemoExample
  /** The LDES server that gets the event stream */
  server: Instance
  /** The LDIO that runs the pipeline; without one there is no pipeline, and data is ingested directly */
  ldio?: Instance
  /** The name of the event stream, when not the one of the example */
  collection?: string
  parts: InstallParts
}

export interface InstallParts {
  stream: boolean
  views: boolean
  pipeline: boolean
  /** Only for examples with sample data */
  data: boolean
}

const PARALLEL_INGESTS = 6

export type StepStatus = 'pending' | 'running' | 'done' | 'skipped' | 'failed'

export interface InstallStep {
  part: keyof InstallParts
  label: string
  status: StepStatus
  detail?: string
}

export interface InstallOptions {
  client?: LdesAdminApi
  /** Called with a copy of the steps whenever one changes */
  onChange?: (steps: InstallStep[]) => void
  /** How long to wait for a new pipeline to run before sending it data */
  pipelineTimeoutMs?: number
  pollMs?: number
}

/** The steps an installation will take, before it starts */
export function plannedSteps(request: InstallRequest): InstallStep[] {
  const { example, ldio, parts } = request
  const collection = request.collection ?? example.collection
  const pipeline = examplePipelineName(collection)
  const steps: InstallStep[] = []
  const step = (part: keyof InstallParts, label: string) => steps.push({ part, label, status: 'pending' })
  if (parts.stream) step('stream', `Event stream ${collection}`)
  if (parts.views) step('views', 'Views by-page and by-time')
  if (parts.pipeline && ldio) step('pipeline', `Pipeline ${pipeline} on ${ldio.name}`)
  if (parts.data && example.sampleData) {
    step('data', parts.pipeline && ldio
      ? `${example.sampleData.description}, through the pipeline`
      : `${example.sampleData.description}, ingested directly`)
  }
  return steps
}

/** Where an LDIO posts to the event stream: the internal URL of a local server, else its URL */
export function eventStreamUrl(server: Instance, collection: string): string {
  return `${server.internalUrl ?? server.url}/${collection}`
}

/**
 * Takes the steps one by one and reports their progress. The first one that fails stops the
 * installation; the steps after it are skipped. Resolves with the steps as they ended.
 */
export async function installExample(request: InstallRequest, options: InstallOptions = {}):
  Promise<InstallStep[]> {
  const client = options.client ?? defaultApi
  const { example, server, ldio } = request
  const collection = request.collection ?? example.collection
  const steps = plannedSteps(request)
  const report = () => options.onChange?.(steps.map((step) => ({ ...step })))
  const update = (step: InstallStep, status: StepStatus, detail?: string) => {
    step.status = status
    step.detail = detail
    report()
  }
  let pipelineName: string | undefined

  const run: Record<keyof InstallParts, (step: InstallStep) => Promise<void>> = {
    async stream(step) {
      const streams = await client.listEventStreams(server.id)
      if (streams.some((stream) => stream.collection === collection)) {
        update(step, 'skipped', 'Exists already')
        return
      }
      await client.createEventStream(server.id, example.eventStream(server.url, collection), 'text/turtle')
      update(step, 'done')
    },
    async views(step) {
      const existing = (await client.listViews(server.id, collection)).map((view) => view.name)
      const missing = exampleViews(`${server.url}/${collection}`, example.timestampPath)
        .filter((view) => !existing.includes(view.name))
      for (const view of missing) await client.createView(server.id, collection, view.definition)
      update(step, missing.length ? 'done' : 'skipped', missing.length ? undefined : 'Exist already')
    },
    async pipeline(step) {
      const pipeline = example.pipeline(collection, eventStreamUrl(server, collection))
      const deployed = (await client.listPipelines(ldio!.id)).map((candidate) => candidate.pipeline)
      // A pipeline of the same name may feed another LDES server; then this one needs another name
      const same = deployed.find((candidate) => candidate.name === pipeline.name)
      if (same && feedsSameStream(same, pipeline)) {
        pipelineName = same.name
        update(step, 'skipped', 'Deployed already')
        return
      }
      pipelineName = freeName(pipeline.name, deployed.map((candidate) => candidate.name))
      step.label = `Pipeline ${pipelineName} on ${ldio!.name}`
      await client.createPipeline(ldio!.id, { ...pipeline, name: pipelineName })
      update(step, 'done')
    },
    async data(step) {
      const now = new Date()
      if (pipelineName) {
        await untilRunning(client, ldio!.id, pipelineName, options)
        const message = example.sampleData!.message(now)
        await client.sendToPipeline(ldio!.id, pipelineName, message.content, message.contentType)
        update(step, 'done')
        return
      }
      // A few at a time: one request per member, as the server takes one member at a time
      const members = example.sampleData!.members(now)
      for (let done = 0; done < members.length; done += PARALLEL_INGESTS) {
        update(step, 'running', `${done} of ${members.length}`)
        await Promise.all(members.slice(done, done + PARALLEL_INGESTS)
          .map((member) => client.ingestMember(server.id, collection, member, 'text/turtle')))
      }
      update(step, 'done')
    },
  }

  for (const [index, step] of steps.entries()) {
    update(step, 'running')
    try {
      await run[step.part](step)
    } catch (e) {
      update(step, 'failed', errorMessage(e))
      steps.slice(index + 1).forEach((later) => {
        later.status = 'skipped'
        later.detail = 'Not done, after the error above'
      })
      report()
      break
    }
  }
  return steps
}

export function failed(steps: InstallStep[]): boolean {
  return steps.some((step) => step.status === 'failed')
}

function feedsSameStream(deployed: Pipeline, pipeline: Pipeline): boolean {
  const endpoints = (outputs: ComponentDefinition[] | undefined) => (outputs ?? [])
    .filter((output) => output.name === 'Ldio:HttpOut')
    .map((output) => String(output.config?.endpoint ?? ''))
  return endpoints(deployed.outputs).some((endpoint) => endpoints(pipeline.outputs).includes(endpoint))
}

function freeName(name: string, taken: string[]): string {
  let candidate = name
  for (let i = 2; taken.includes(candidate); i++) candidate = `${name}-${i}`
  return candidate
}

/** A new pipeline takes a moment before it accepts data over HTTP */
async function untilRunning(client: LdesAdminApi, ldioId: string, name: string, options: InstallOptions) {
  const deadline = Date.now() + (options.pipelineTimeoutMs ?? 60_000)
  for (;;) {
    const status = (await client.listPipelines(ldioId)).find((deployed) => deployed.pipeline.name === name)?.status
    if (status === 'RUNNING') return
    if (status === 'HALTED' || status === 'STOPPED') throw new Error(`Pipeline ${name} is ${status.toLowerCase()}`)
    if (Date.now() > deadline) throw new Error(`Pipeline ${name} did not start running in time`)
    await new Promise((resolve) => setTimeout(resolve, options.pollMs ?? 1000))
  }
}
