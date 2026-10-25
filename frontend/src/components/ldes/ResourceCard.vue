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
 * A fragmentation or retention policy of a definition: its kind, which can be changed, and its
 * properties. Fragmentations come in order, so they can be moved.
 */
const props = defineProps<{
  graph: RdfGraph
  node: Resource
  kinds: ResourceKind[]
  /** What the resource is, e.g. "fragmentation" */
  noun: string
  problems: DefinitionProblem[]
  stream?: StreamContext
  /** The place in an ordered list; only then can it move */
  position?: { index: number; count: number }
}>()
const emit = defineEmits<{ change: [graph: RdfGraph]; remove: []; move: [offset: number] }>()

const kind = computed(() => kindOf(props.graph, props.node, props.kinds))
const type = computed(() => objectOf(props.graph, props.node, RDF_TYPE)?.value)
const options = computed(() => props.kinds.map((candidate) => ({ label: candidate.label, value: candidate.type })))
const order = computed(() => propertyOrder(kind.value?.properties ?? []))
const scope = computed(() => `${props.noun.replace(/\s/g, '-')}-${props.position?.index ?? props.node.value}`)

function changeType(value: string) {
  const to = props.kinds.find((candidate) => candidate.type === value)
  if (to) emit('change', changeKind(props.graph, props.node, kind.value, to, props.stream))
}
</script>

<template>
  <article class="resource-card" :data-cy="`${noun.replace(/\s/g, '-')}-card`">
    <header class="head">
      <span v-if="position" class="index" :aria-label="`Level ${position.index + 1}`">{{ position.index + 1 }}</span>
      <i v-else :class="kind?.icon ?? 'pi pi-question-circle'" class="icon" />
      <Select :model-value="kind?.type" :options="options" option-label="label" option-value="value" size="small"
        :placeholder="type ? `Unknown: ${predicateKey(graph, type)}` : `Choose the kind of ${noun}`"
        :aria-label="`Kind of ${noun}`" class="kind" data-cy="resource-kind"
        @update:model-value="changeType" />
      <code v-if="type" class="muted type truncate">{{ predicateKey(graph, type) }}</code>
      <span class="spacer" />
      <a v-if="kind" :href="kind.docs" target="_blank" rel="noopener" class="docs" v-tooltip.top="'Documentation'"
        :aria-label="`Documentation of ${kind.label.toLowerCase()} ${noun}`"><i class="pi pi-external-link" /></a>
      <template v-if="position">
        <Button icon="pi pi-arrow-up" text rounded size="small" severity="secondary" :disabled="position.index === 0"
          aria-label="Move up" v-tooltip.top="'Move up'" @click="emit('move', -1)" />
        <Button icon="pi pi-arrow-down" text rounded size="small" severity="secondary"
          :disabled="position.index === position.count - 1" aria-label="Move down" v-tooltip.top="'Move down'"
          @click="emit('move', 1)" />
      </template>
      <Button icon="pi pi-trash" text rounded size="small" severity="secondary" :aria-label="`Remove ${noun}`"
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

.index {
  width: 22px;
  height: 22px;
  flex: none;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--p-primary-color);
  background: color-mix(in srgb, var(--p-primary-500) 14%, transparent);
}

.icon {
  color: var(--p-primary-color);
}

.kind {
  min-width: 150px;
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
