<script setup lang="ts">
import { computed } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import type { ResourceKind, StreamContext } from '@/ldes/catalog'
import {
  changeKind,
  type DefinitionProblem,
  kindOf,
  predicateKey,
  propertyOrder,
  writeValue,
} from '@/ldes/definition'
import { objectOf, RDF_TYPE, type RdfGraph, type Resource, setObjects } from '@/ldes/turtle'
import DefinitionFields from './DefinitionFields.vue'

/**
 * A fragmentation or retention policy of a definition: its kind, which can be changed unless it is
 * fixed, and its properties.
 */
const props = defineProps<{
  graph: RdfGraph
  node: Resource
  kinds: ResourceKind[]
  /** What the resource is, e.g. "fragmentation" */
  noun: string
  problems: DefinitionProblem[]
  stream?: StreamContext
  /** Whether there is one and of what kind is decided elsewhere, e.g. by the template of a view */
  fixed?: boolean
}>()
const emit = defineEmits<{ change: [graph: RdfGraph]; remove: [] }>()

const kind = computed(() => kindOf(props.graph, props.node, props.kinds))
const type = computed(() => objectOf(props.graph, props.node, RDF_TYPE)?.value)
const options = computed(() => props.kinds.map((candidate) => ({ label: candidate.label, value: candidate.type })))
const order = computed(() => propertyOrder(kind.value?.properties ?? []))
const scope = computed(() => `${props.noun.replace(/\s/g, '-')}-${props.node.value}`)

function changeType(value: string) {
  const to = props.kinds.find((candidate) => candidate.type === value)
  if (to) emit('change', changeKind(props.graph, props.node, kind.value, to, props.stream))
}
</script>

<template>
  <article class="resource-card" :data-cy="`${noun.replace(/\s/g, '-')}-card`">
    <header class="head">
      <i :class="kind?.icon ?? 'pi pi-question-circle'" class="icon" />
      <span v-if="fixed" class="kind-label" data-cy="resource-kind">{{ kind?.label ?? `Unknown ${noun}` }}</span>
      <Select v-else :model-value="kind?.type" :options="options" option-label="label" option-value="value" size="small"
        :placeholder="type ? `Unknown: ${predicateKey(graph, type)}` : `Choose the kind of ${noun}`"
        :aria-label="`Kind of ${noun}`" class="kind" data-cy="resource-kind"
        @update:model-value="changeType" />
      <code v-if="type" class="muted type truncate">{{ predicateKey(graph, type) }}</code>
      <span class="spacer" />
      <a v-if="kind" :href="kind.docs" target="_blank" rel="noopener" class="docs" v-tooltip.top="'Documentation'"
        :aria-label="`Documentation of ${kind.label.toLowerCase()} ${noun}`"><i class="pi pi-external-link" /></a>
      <Button v-if="!fixed" icon="pi pi-trash" text rounded size="small" severity="secondary" :aria-label="`Remove ${noun}`"
        v-tooltip.top="'Remove'" data-cy="remove-resource" @click="emit('remove')" />
    </header>
    <p v-if="kind" class="muted small description">{{ kind.description }}</p>
    <DefinitionFields :graph="graph" :node="node" :properties="kind?.properties ?? []" :problems="problems"
      :owner="kind ? `a ${kind.label.toLowerCase()} ${noun}` : `this ${noun}`" :scope="scope"
      @change="(property, value) => emit('change', writeValue(graph, node, property, value, order))"
      @remove="(predicate) => emit('change', setObjects(graph, node, predicate, []))" />
  </article>
</template>

<style scoped>
.resource-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 14px 16px;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.icon {
  color: var(--p-primary-color);
}

.kind {
  min-width: 150px;
}

.kind-label {
  font-weight: 600;
}

.type {
  font-size: 11.5px;
  min-width: 0;
}

.docs {
  color: var(--muted);
  font-size: 12px;
  padding: 0 4px;
}

.description {
  margin: -4px 0 0;
}
</style>
