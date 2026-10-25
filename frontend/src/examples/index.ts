import type { EventStream } from '@/domain/ldes'
import { PREFIXES } from '@/domain/rdf'
import { objectsOf, parseTurtle, subjectsWith } from '@/ldes/turtle'
import type { DemoExample } from './example'
import { parking } from './parking'
import { sensors } from './sensors'

export type { DemoExample, ExampleMember, SampleData } from './example'
export { EXAMPLE_VIEWS, exampleViews, examplePipelineName } from './example'

export const examples: DemoExample[] = [sensors, parking]

export function exampleById(id: string | undefined): DemoExample | undefined {
  return examples.find((example) => example.id === id)
}

/**
 * The example whose members fit an event stream: one with the same timestamp path, that has the
 * name of the example or a shape targeting the members of the example. Its members are then the
 * ones to start from when ingesting.
 */
export function exampleFor(stream: EventStream): DemoExample | undefined {
  return examples.find((example) => stream.timestampPath === example.timestampPath
    && (stream.collection === example.collection || targetClasses(stream).includes(example.memberClass)))
}

function targetClasses(stream: EventStream): string[] {
  const parsed = parseTurtle(stream.definition)
  if (!parsed.ok) return []
  const targetClass = `${PREFIXES.sh}targetClass`
  return subjectsWith(parsed.graph, targetClass)
    .flatMap((shape) => objectsOf(parsed.graph, shape, targetClass).map((term) => term.value))
}
