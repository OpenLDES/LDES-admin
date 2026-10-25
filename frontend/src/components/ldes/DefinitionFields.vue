<script setup lang="ts">
import { computed } from 'vue'
import Button from 'primevue/button'
import ConfigField from '@/components/common/ConfigField.vue'
import type { RdfProperty } from '@/ldes/catalog'
import {
  type DefinitionProblem,
  isApplicable,
  predicateKey,
  readValue,
  unknownPredicates,
} from '@/ldes/definition'
import { objectsOf, type RdfGraph, type Resource, termText } from '@/ldes/turtle'

/**
 * The properties of a resource in a definition as a form: a field per property that applies, the
 * problems that belong to no field, and predicates that are no property at all.
 */
const props = defineProps<{
  graph: RdfGraph
  /** Undefined while the resource does not exist yet, e.g. a Kafka source */
  node: Resource | undefined
  properties: RdfProperty[]
  problems: DefinitionProblem[]
  /** What the resource is, e.g. "an event stream" */
  owner: string
  /** Predicates that belong to the resource without being a property in this form */
  others?: string[]
  scope?: string
}>()
const emit = defineEmits<{ change: [property: RdfProperty, value: unknown]; remove: [predicate: string] }>()

const shown = computed(() => props.properties.filter((property) =>
  isApplicable(props.graph, props.node, props.properties, property)
  || readValue(props.graph, props.node, property) !== undefined))

const unknown = computed(() => unknownPredicates(props.graph, props.node, props.properties, props.others))

const own = computed(() => props.problems.filter((problem) => problem.node && props.node
  && problem.node.equals(props.node)))

/** Problems of the resource that no field shows, e.g. a missing shape */
const general = computed(() => {
  const keys = new Set([
    ...props.properties.map((property) => property.key),
    ...unknown.value.map((predicate) => predicateKey(props.graph, predicate)),
  ])
  return own.value.filter((problem) => problem.key === undefined || !keys.has(problem.key))
})

function problemsOf(key: string) {
  return own.value.filter((problem) => problem.key === key)
}

function preview(predicate: string) {
  return objectsOf(props.graph, props.node!, predicate)
    .map((object) => termText(props.graph, object) || '[ … ]').join(', ')
}
</script>

<template>
  <div class="definition-fields">
    <small v-for="problem in general" :key="problem.message" class="problem" :class="problem.severity"
      data-cy="resource-problem">
      <i :class="problem.severity === 'error' ? 'pi pi-times-circle' : 'pi pi-exclamation-triangle'" />
      {{ problem.message }}
    </small>
    <ConfigField v-for="property in shown" :key="property.key" :property="property"
      :value="readValue(graph, node, property)" :problems="problemsOf(property.key)" :scope="scope"
      @change="emit('change', property, $event)" />
    <section v-if="unknown.length" class="others" data-cy="other-properties">
      <span class="others-title">Not properties of {{ owner }}</span>
      <div v-for="predicate in unknown" :key="predicate" class="other">
        <code class="truncate">{{ predicateKey(graph, predicate) }} {{ preview(predicate) }}</code>
        <Button icon="pi pi-trash" text rounded size="small" severity="secondary"
          :aria-label="`Remove ${predicateKey(graph, predicate)}`" v-tooltip.left="'Remove'"
          @click="emit('remove', predicate)" />
      </div>
      <small class="muted">The server ignores these. A typo, perhaps?</small>
    </section>
  </div>
</template>

<style scoped>
.definition-fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.problem {
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1.4;
}

.problem .pi {
  font-size: 11px;
}

.problem.error {
  color: var(--p-red-600);
}

.problem.warning {
  color: var(--p-amber-700);
}

.app-dark .problem.error {
  color: var(--p-red-300);
}

.app-dark .problem.warning {
  color: var(--p-amber-300);
}

.others {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--p-amber-500) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--p-amber-500) 25%, transparent);
}

.others-title {
  font-size: 12.5px;
  font-weight: 600;
}

.other {
  display: flex;
  align-items: center;
  gap: 6px;
}

.other code {
  font-size: 12px;
  flex: 1;
  min-width: 0;
}
</style>
