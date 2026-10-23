import { parse, stringify } from 'yaml'
import type { ComponentConfig, ComponentDefinition, Pipeline } from '@/domain/ldio'
import { componentInfo, type ComponentKind, kinds } from './catalog'

/**
 * YAML in the format of LDIO pipeline definitions, for a whole pipeline or for a single component
 * (as edited in the sandbox inspector).
 */
const options = { indent: 2, lineWidth: 0 } as const

export type ParsedComponent =
  | { ok: true; name: string; config: ComponentConfig }
  | { ok: false; error: string }

export type ParsedPipeline = { ok: true; pipeline: Pipeline } | { ok: false; error: string }

export function componentToYaml(name: string, config: ComponentConfig): string {
  return stringify({ name, config }, options)
}

/**
 * Parses the YAML of one component, e.g.
 *
 * <pre>
 * name: Ldio:HttpOut
 * config:
 *   endpoint: http://localhost:8080/parcels
 * </pre>
 *
 * @param kind the kind of node being edited; the component must be of the same kind
 */
export function parseComponentYaml(text: string, kind?: ComponentKind): ParsedComponent {
  let value: unknown
  try {
    value = parse(text)
  } catch (e) {
    return { ok: false, error: firstLine(e) }
  }
  if (!isObject(value)) {
    return { ok: false, error: "Expected a 'name' and optionally a 'config'" }
  }
  const unexpected = Object.keys(value).filter((key) => key !== 'name' && key !== 'config')
  if (unexpected.includes('adapter')) {
    return { ok: false, error: 'The adapter is a separate component: connect it to the input' }
  }
  if (unexpected.length > 0) {
    return { ok: false, error: `Unexpected key '${unexpected[0]}'` }
  }
  if (typeof value.name !== 'string' || value.name.trim() === '') {
    return { ok: false, error: "'name' must be the id of a component, e.g. Ldio:HttpOut" }
  }
  const config = value.config ?? {}
  if (!isObject(config)) {
    return { ok: false, error: "'config' must be a map of properties" }
  }
  const info = componentInfo(value.name)
  if (!info) {
    return { ok: false, error: `Unknown component '${value.name}'` }
  }
  if (kind && info.kind !== kind) {
    return {
      ok: false,
      error: `${value.name} is ${article(info.kind)} ${kinds[info.kind].label.toLowerCase()}, but this component is ${article(kind)} ${kinds[kind].label.toLowerCase()}`,
    }
  }
  return { ok: true, name: value.name, config }
}

export function pipelineToYaml(pipeline: Pipeline): string {
  const document: Record<string, unknown> = { name: pipeline.name }
  if (pipeline.description) document.description = pipeline.description
  const { adapter, ...input } = pipeline.input
  document.input = adapter ? { ...withoutEmptyConfig(input), adapter: withoutEmptyConfig(adapter) }
    : withoutEmptyConfig(input)
  if (pipeline.transformers?.length) {
    document.transformers = pipeline.transformers.map(withoutEmptyConfig)
  }
  document.outputs = (pipeline.outputs ?? []).map(withoutEmptyConfig)
  return stringify(document, options)
}

export function parsePipelineYaml(text: string): ParsedPipeline {
  let value: unknown
  try {
    value = parse(text)
  } catch (e) {
    return { ok: false, error: firstLine(e) }
  }
  if (!isObject(value)) {
    return { ok: false, error: 'Expected a pipeline definition' }
  }
  if (typeof value.name !== 'string') {
    return { ok: false, error: "A pipeline needs a 'name'" }
  }
  if (!isComponent(value.input)) {
    return { ok: false, error: "A pipeline needs an 'input' with a 'name'" }
  }
  const adapter = value.input.adapter
  if (adapter !== undefined && !isComponent(adapter)) {
    return { ok: false, error: "The 'adapter' of the input needs a 'name'" }
  }
  for (const key of ['transformers', 'outputs'] as const) {
    const list = value[key]
    if (list !== undefined && list !== null
      && (!Array.isArray(list) || !list.every(isComponent))) {
      return { ok: false, error: `'${key}' must be a list of components with a 'name'` }
    }
  }
  const unknown = [
    value.input,
    adapter,
    ...((value.transformers as unknown[]) ?? []),
    ...((value.outputs as unknown[]) ?? []),
  ]
    .filter(isComponent)
    .find((component) => !componentInfo(component.name))
  if (unknown && isComponent(unknown)) {
    return { ok: false, error: `Unknown component '${unknown.name}'` }
  }
  return {
    ok: true,
    pipeline: {
      name: value.name,
      description: typeof value.description === 'string' ? value.description : undefined,
      input: {
        name: value.input.name,
        config: configOf(value.input),
        ...(isComponent(adapter) ? { adapter: { name: adapter.name, config: configOf(adapter) } } : {}),
      },
      transformers: ((value.transformers as ComponentDefinition[]) ?? []).map(normalize),
      outputs: ((value.outputs as ComponentDefinition[]) ?? []).map(normalize),
    },
  }
}

function normalize(component: ComponentDefinition): ComponentDefinition {
  return { name: component.name, config: configOf(component) }
}

function configOf(component: { config?: unknown }): ComponentConfig {
  return isObject(component.config) ? component.config : {}
}

function withoutEmptyConfig(component: ComponentDefinition): ComponentDefinition {
  return component.config && Object.keys(component.config).length > 0
    ? { name: component.name, config: component.config }
    : { name: component.name }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isComponent(value: unknown): value is ComponentDefinition & Record<string, unknown> {
  return isObject(value) && typeof value.name === 'string'
}

function article(kind: ComponentKind): string {
  return kind === 'input' || kind === 'adapter' || kind === 'output' ? 'an' : 'a'
}

function firstLine(error: unknown): string {
  return (error instanceof Error ? error.message : String(error)).split('\n')[0]
}
