import { DataFactory } from 'n3'
import type { NamedNode } from 'n3'
import { PREFIXES, prefixLines } from '@/domain/rdf'
import {
  EVENT_STREAM_ORDER,
  eventStreamProperties,
  kafkaSourceProperties,
  type RdfProperty,
  type ResourceKind,
  retentionPolicyKinds,
} from './catalog'
import {
  createResource,
  type DefinitionProblem,
  localName,
  nameProblem,
  propertyOrder,
  resourceProblems,
  retentionPolicyProblems,
  writeValue,
} from './definition'
import {
  addResource,
  isResource,
  objectOf,
  objectsOf,
  parseTurtle,
  predicatesOf,
  RDF_TYPE,
  type RdfGraph,
  renameResource,
  type Resource,
  setObjects,
  subjectsWith,
  termText,
} from './turtle'

/**
 * The definition of an event stream, as the admin API of an LDES server takes it, and what the
 * form changes in it: the collection name, the paths, version creation, the retention of the event
 * source and a Kafka source.
 */
const { ldes, tree, sh } = PREFIXES
const { namedNode } = DataFactory

const EVENT_STREAM = `${ldes}EventStream`
const EVENT_SOURCE = `${ldes}EventSource`
const EVENT_SOURCE_PROPERTY = `${ldes}eventSource`
const KAFKA_SOURCE = `${ldes}kafkaSource`
const RETENTION_POLICY = `${ldes}retentionPolicy`
const SHAPE = `${tree}shape`

export function eventStreamTemplate(serverUrl: string, collection: string): RdfGraph {
  const parsed = parseTurtle(`${prefixLines('ldes', 'dcterms', 'tree', 'sh')}
@prefix server:     <${serverUrl}/> .

server:${collection} a ldes:EventStream ;
    ldes:timestampPath dcterms:created ;
    ldes:versionOfPath dcterms:isVersionOf ;
    tree:shape [ a sh:NodeShape ] .
`)
  if (!parsed.ok) throw new Error(parsed.error)
  return parsed.graph
}

/** The event stream of the definition: the first subject of type ldes:EventStream */
export function streamOf(graph: RdfGraph): NamedNode | undefined {
  return subjectsWith(graph, RDF_TYPE, EVENT_STREAM).find((subject) => subject.termType === 'NamedNode') as
    NamedNode | undefined
}

export function collectionOf(graph: RdfGraph): string | undefined {
  const stream = streamOf(graph)
  return stream && localName(stream.value)
}

/** Gives the event stream another collection name, keeping its namespace */
export function renameCollection(graph: RdfGraph, collection: string): RdfGraph {
  const stream = streamOf(graph)
  if (!stream) return graph
  const namespace = stream.value.slice(0, stream.value.length - localName(stream.value).length)
  return renameResource(graph, stream, namedNode(namespace + collection))
}

export function writeStreamValue(graph: RdfGraph, property: RdfProperty, value: unknown): RdfGraph {
  const stream = streamOf(graph)
  return stream ? writeValue(graph, stream, property, value, EVENT_STREAM_ORDER) : graph
}

// Retention of the event source ---------------------------------------------------------------

export function eventSourceOf(graph: RdfGraph): Resource | undefined {
  const stream = streamOf(graph)
  const source = stream && objectOf(graph, stream, EVENT_SOURCE_PROPERTY)
  return source?.termType === 'BlankNode' || source?.termType === 'NamedNode' ? source : undefined
}

export function eventSourcePolicies(graph: RdfGraph): Resource[] {
  const source = eventSourceOf(graph)
  return source ? objectsOf(graph, source, RETENTION_POLICY).filter(isResource) : []
}

/** Adds a retention policy to the event source, which is created when there is none yet */
export function addEventSourcePolicy(graph: RdfGraph, kind: ResourceKind): RdfGraph {
  const stream = streamOf(graph)
  if (!stream) return graph
  let result = graph
  let source = eventSourceOf(graph)
  if (!source) {
    const created = addResource(result, [[RDF_TYPE, namedNode(EVENT_SOURCE)]])
    source = created.node
    result = setObjects(created.graph, stream, EVENT_SOURCE_PROPERTY, [source], EVENT_STREAM_ORDER)
  }
  const policy = createResource(result, kind)
  return setObjects(policy.graph, source, RETENTION_POLICY,
    [...objectsOf(policy.graph, source, RETENTION_POLICY), policy.node])
}

/** Removes a retention policy, and the event source with the last one */
export function removeEventSourcePolicy(graph: RdfGraph, policy: Resource): RdfGraph {
  const stream = streamOf(graph)
  const source = eventSourceOf(graph)
  if (!stream || !source) return graph
  const remaining = objectsOf(graph, source, RETENTION_POLICY).filter((other) => !other.equals(policy))
  const result = setObjects(graph, source, RETENTION_POLICY, remaining)
  const onlyTyped = predicatesOf(result, source).every((predicate) => predicate === RDF_TYPE)
  return onlyTyped && source.termType === 'BlankNode' ? setObjects(result, stream, EVENT_SOURCE_PROPERTY, []) : result
}

// Kafka source --------------------------------------------------------------------------------

export function kafkaSourceOf(graph: RdfGraph): Resource | undefined {
  const stream = streamOf(graph)
  const source = stream && objectOf(graph, stream, KAFKA_SOURCE)
  return source && isResource(source) ? source : undefined
}

/** Sets a property of the Kafka source: the first one creates it, removing the last one removes it */
export function writeKafkaValue(graph: RdfGraph, property: RdfProperty, value: unknown): RdfGraph {
  const stream = streamOf(graph)
  if (!stream) return graph
  let result = graph
  let source = kafkaSourceOf(graph)
  if (!source) {
    const created = addResource(result, [])
    source = created.node
    result = setObjects(created.graph, stream, KAFKA_SOURCE, [source], EVENT_STREAM_ORDER)
  }
  result = writeValue(result, source, property, value, propertyOrder(kafkaSourceProperties))
  return predicatesOf(result, source).length === 0 ? setObjects(result, stream, KAFKA_SOURCE, []) : result
}

// Validation -----------------------------------------------------------------------------------

/** Everything wrong with the definition of an event stream, as far as the form knows */
export function eventStreamProblems(graph: RdfGraph): DefinitionProblem[] {
  const streams = subjectsWith(graph, RDF_TYPE, EVENT_STREAM)
  const stream = streamOf(graph)
  if (!stream) {
    return [{ severity: 'error', message: 'No event stream: the definition needs an IRI of type ldes:EventStream' }]
  }
  const problems: DefinitionProblem[] = []
  if (streams.length > 1) {
    problems.push({ severity: 'error', message: 'Define one event stream at a time' })
  }
  const name = nameProblem(localName(stream.value))
  if (name) problems.push({ node: stream, key: 'collection', severity: 'error', message: name })
  problems.push(...resourceProblems(graph, stream, eventStreamProperties, 'an event stream',
    [EVENT_SOURCE_PROPERTY, KAFKA_SOURCE, SHAPE]))

  const shapes = objectsOf(graph, stream, SHAPE)
  const shape = (message: string) => problems.push({ node: stream, key: 'tree:shape', severity: 'error', message })
  if (shapes.length === 0) shape('Needs a tree:shape, a sh:NodeShape the members must conform to')
  else if (shapes.length > 1) shape('Needs exactly one tree:shape')
  else if (!objectsOf(graph, shapes[0], RDF_TYPE).some((type) => type.value === `${sh}NodeShape`)) {
    shape('The tree:shape must be of type sh:NodeShape')
  }

  const source = objectOf(graph, stream, EVENT_SOURCE_PROPERTY)
  if (source && !objectsOf(graph, source, RDF_TYPE).some((type) => type.value === EVENT_SOURCE)) {
    problems.push({ node: stream, key: 'ldes:eventSource', severity: 'error', message: 'The ldes:eventSource must be of type ldes:EventSource' })
  }
  problems.push(...retentionPolicyProblems(graph, eventSourcePolicies(graph), retentionPolicyKinds))

  const kafka = kafkaSourceOf(graph)
  if (kafka) problems.push(...resourceProblems(graph, kafka, kafkaSourceProperties, 'a Kafka source'))

  // The server closes the definition: anything that hangs loose may only have a type
  for (const subject of loose(graph)) {
    if (subject.equals(stream) || streams.some((other) => other.equals(subject))) continue
    problems.push({
      severity: 'error',
      message: `${termText(graph, subject) || 'A blank node'} is not part of the event stream; the server rejects statements about it`,
    })
  }
  return problems
}

/** Subjects nothing refers to, with more than a type */
function loose(graph: RdfGraph): Resource[] {
  const subjects: Resource[] = []
  for (const { subject, predicate } of graph.quads) {
    if (predicate.value === RDF_TYPE || !isResource(subject)) continue
    if (subjects.some((other) => other.equals(subject))) continue
    if (!graph.quads.some((other) => other.object.equals(subject))) subjects.push(subject)
  }
  return subjects
}
