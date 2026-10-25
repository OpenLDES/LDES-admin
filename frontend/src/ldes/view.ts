import { DataFactory } from 'n3'
import type { NamedNode } from 'n3'
import { PREFIXES, prefixLines } from '@/domain/rdf'
import {
  fragmentationKind,
  fragmentationKinds,
  type RdfProperty,
  type ResourceKind,
  retentionPolicyKind,
  retentionPolicyKinds,
  type StreamContext,
  VIEW_DESCRIPTION_ORDER,
  viewDescriptionProperties,
} from './catalog'
import {
  createResource,
  type DefinitionProblem,
  kindOf,
  localName,
  nameProblem,
  predicateKey,
  resourceProblems,
  retentionPolicyProblems,
  writeValue,
} from './definition'
import {
  isResource,
  listItems,
  objectOf,
  objectsOf,
  parseTurtle,
  RDF_TYPE,
  type RdfGraph,
  renameResource,
  type Resource,
  setList,
  setObjects,
  subjectsWith,
} from './turtle'

/**
 * The definition of a view, as the admin API of an LDES server takes it, and what the form changes
 * in it: the name, the page size, the fragmentation and the retention policies.
 */
const { ldes, tree } = PREFIXES
const { namedNode } = DataFactory

const VIEW_DESCRIPTION = `${tree}viewDescription`
const FRAGMENTATION_STRATEGY = `${tree}fragmentationStrategy`
const RETENTION_POLICY = `${ldes}retentionPolicy`

/** A kind of view to start from; it decides the fragmentation, if any: the form cannot add one */
export interface ViewPreset {
  id: string
  title: string
  description: string
  icon: string
  name: string
  fragmentation?: ResourceKind
  retentionPolicy?: ResourceKind
}

export const viewPresets: ViewPreset[] = [
  { id: 'pagination', title: 'Pagination', description: 'Pages of a fixed size, in ingest order', icon: 'pi pi-book', name: 'by-page' },
  {
    id: 'time',
    title: 'Time-based',
    description: 'Buckets per year, month and day',
    icon: 'pi pi-calendar',
    name: 'by-time',
    fragmentation: fragmentationKind(`${tree}HierarchicalTimeBasedFragmentation`),
  },
  {
    id: 'geo',
    title: 'Geospatial',
    description: 'Map tiles at zoom level 15',
    icon: 'pi pi-map',
    name: 'by-location',
    fragmentation: fragmentationKind(`${tree}GeospatialFragmentation`),
  },
  {
    id: 'reference',
    title: 'Reference',
    description: 'A bucket per state object',
    icon: 'pi pi-tag',
    name: 'by-reference',
    fragmentation: fragmentationKind(`${tree}ReferenceFragmentation`),
  },
  {
    id: 'retention',
    title: 'Recent members',
    description: 'Only members of the last 30 days',
    icon: 'pi pi-clock',
    name: 'recent',
    retentionPolicy: retentionPolicyKind(`${ldes}DurationAgoPolicy`),
  },
]

/** The definition of a new view on an event stream, after a preset */
export function viewTemplate(preset: ViewPreset, streamUrl: string, stream: StreamContext): RdfGraph {
  const parsed = parseTurtle(`${prefixLines('ldes', 'tree', 'xsd')}
@prefix server:     <${streamUrl}/> .

server:${preset.name} tree:viewDescription [
    tree:fragmentationStrategy () ;
    tree:pageSize "250"^^xsd:int
] .
`)
  if (!parsed.ok) throw new Error(parsed.error)
  let graph = parsed.graph
  if (preset.fragmentation) graph = setFragmentation(graph, preset.fragmentation, stream)
  if (preset.retentionPolicy) graph = addViewRetentionPolicy(graph, preset.retentionPolicy)
  return graph
}

/** The view of the definition: the first IRI with a tree:viewDescription */
export function viewOf(graph: RdfGraph): NamedNode | undefined {
  return subjectsWith(graph, VIEW_DESCRIPTION).find((subject) => subject.termType === 'NamedNode') as
    NamedNode | undefined
}

export function viewNameOf(graph: RdfGraph): string | undefined {
  const view = viewOf(graph)
  return view && localName(view.value)
}

export function descriptionOf(graph: RdfGraph): Resource | undefined {
  const view = viewOf(graph)
  const description = view && objectOf(graph, view, VIEW_DESCRIPTION)
  return description?.termType === 'BlankNode' || description?.termType === 'NamedNode' ? description : undefined
}

/** Gives the view another name, keeping its namespace */
export function renameView(graph: RdfGraph, name: string): RdfGraph {
  const view = viewOf(graph)
  if (!view) return graph
  const namespace = view.value.slice(0, view.value.length - localName(view.value).length)
  return renameResource(graph, view, namedNode(namespace + name))
}

export function writeDescriptionValue(graph: RdfGraph, property: RdfProperty, value: unknown): RdfGraph {
  const description = descriptionOf(graph)
  return description ? writeValue(graph, description, property, value, VIEW_DESCRIPTION_ORDER) : graph
}

// Fragmentation -------------------------------------------------------------------------------

/**
 * The fragmentations of the view; undefined when they are not a list. The definition holds a list,
 * but a view has one fragmentation at most.
 */
export function fragmentationsOf(graph: RdfGraph): Resource[] | undefined {
  const description = descriptionOf(graph)
  if (!description) return undefined
  const strategies = objectsOf(graph, description, FRAGMENTATION_STRATEGY)
  if (strategies.length === 0) return []
  const items = strategies.length === 1 ? listItems(graph, strategies[0]) : undefined
  return items?.every(isResource) ? items : undefined
}

/** Gives a new view the fragmentation of its template */
function setFragmentation(graph: RdfGraph, kind: ResourceKind, stream: StreamContext): RdfGraph {
  const description = descriptionOf(graph)
  if (!description) return graph
  const created = createResource(graph, kind, stream)
  return setList(created.graph, description, FRAGMENTATION_STRATEGY, [created.node], VIEW_DESCRIPTION_ORDER)
}

// Retention policies --------------------------------------------------------------------------

export function viewRetentionPolicies(graph: RdfGraph): Resource[] {
  const description = descriptionOf(graph)
  return description ? objectsOf(graph, description, RETENTION_POLICY).filter(isResource) : []
}

export function addViewRetentionPolicy(graph: RdfGraph, kind: ResourceKind): RdfGraph {
  const description = descriptionOf(graph)
  if (!description) return graph
  const created = createResource(graph, kind)
  return setObjects(created.graph, description, RETENTION_POLICY,
    [...objectsOf(created.graph, description, RETENTION_POLICY), created.node], VIEW_DESCRIPTION_ORDER)
}

export function removeViewRetentionPolicy(graph: RdfGraph, policy: Resource): RdfGraph {
  const description = descriptionOf(graph)
  if (!description) return graph
  return setObjects(graph, description, RETENTION_POLICY,
    objectsOf(graph, description, RETENTION_POLICY).filter((other) => !other.equals(policy)))
}

// Validation -----------------------------------------------------------------------------------

/** Everything wrong with the definition of a view, as far as the form knows */
export function viewProblems(graph: RdfGraph): DefinitionProblem[] {
  const view = viewOf(graph)
  if (!view) {
    return [{ severity: 'error', message: 'No view: the definition needs an IRI with a tree:viewDescription' }]
  }
  const problems: DefinitionProblem[] = []
  if (subjectsWith(graph, VIEW_DESCRIPTION).length > 1) {
    problems.push({ severity: 'error', message: 'Define one view at a time' })
  }
  const name = nameProblem(localName(view.value))
  if (name) problems.push({ node: view, key: 'name', severity: 'error', message: name })
  const description = descriptionOf(graph)
  if (!description) {
    problems.push({ severity: 'error', message: 'The tree:viewDescription must be a resource, e.g. [ … ]' })
    return problems
  }
  problems.push(...resourceProblems(graph, description, viewDescriptionProperties, 'a view description',
    [FRAGMENTATION_STRATEGY, RETENTION_POLICY]))

  const strategy = (message: string) =>
    problems.push({ node: description, key: 'tree:fragmentationStrategy', severity: 'error', message })
  const strategies = objectsOf(graph, description, FRAGMENTATION_STRATEGY)
  const fragmentations = fragmentationsOf(graph)
  if (strategies.length === 0) strategy('Required, even without fragmentations: tree:fragmentationStrategy ()')
  else if (!fragmentations) strategy('Must be one list of fragmentations: ( [ … ] [ … ] )')
  for (const [index, fragmentation] of (fragmentations ?? []).entries()) {
    const types = objectsOf(graph, fragmentation, RDF_TYPE)
    const kind = kindOf(graph, fragmentation, fragmentationKinds)
    const problem = (message: string) => problems.push({ node: fragmentation, severity: 'error', message })
    if (index > 0) problem('A view has one fragmentation at most: remove this one from the definition')
    if (types.length !== 1) problem('A fragmentation has exactly one type')
    else if (types[0].value === `${tree}PaginationFragmentation`) problem('Leave pagination out: the server paginates every view')
    else if (!kind) {
      problem(`Not a fragmentation the server knows: use ${fragmentationKinds.map((k) => predicateKey(graph, k.type)).join(', ')}`)
    }
    if (kind) {
      problems.push(...resourceProblems(graph, fragmentation, kind.properties,
        `a ${kind.label.toLowerCase()} fragmentation`))
    }
  }
  problems.push(...retentionPolicyProblems(graph, viewRetentionPolicies(graph), retentionPolicyKinds))
  return problems
}
