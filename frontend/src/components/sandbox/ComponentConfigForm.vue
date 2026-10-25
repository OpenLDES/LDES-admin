<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import type { ComponentConfig } from '@/domain/ldio'
import { componentInfo, type ConfigProperty } from '@/pipeline/catalog'
import {
  configProblems,
  isApplicable,
  isBlank,
  readProperty,
  removeKey,
  unknownKeys,
  writeProperty,
} from '@/pipeline/config'
import ConfigField from './ConfigField.vue'

/**
 * The configuration of a pipeline component as a form: a field per property that applies, with
 * related properties grouped and collapsed until they are used.
 */
const props = defineProps<{ component: string; config: ComponentConfig | undefined }>()
const emit = defineEmits<{ change: [config: ComponentConfig] }>()

const info = computed(() => componentInfo(props.component))
const problems = computed(() => configProblems(props.component, props.config))
const general = computed(() => problems.value.filter((problem) => problem.key === undefined))

interface Section {
  title: string
  properties: ConfigProperty[]
}

/** Ungrouped properties first, then the groups in catalog order */
const sections = computed<Section[]>(() => {
  const current = info.value
  if (!current) return []
  const shown = current.properties.filter((property) => isApplicable(current, props.config, property)
    || !isBlank(readProperty(props.config, property.key)))
  const sections: Section[] = [{ title: '', properties: [] }]
  for (const property of shown) {
    const title = property.group ?? ''
    let section = sections.find((candidate) => candidate.title === title)
    if (!section) {
      section = { title, properties: [] }
      sections.push(section)
    }
    section.properties.push(property)
  }
  return sections.filter((section) => section.properties.length > 0)
})

const others = computed(() => (info.value ? unknownKeys(info.value, props.config) : []))

function problemsOf(key: string) {
  return problems.value.filter((problem) => problem.key === key)
}

function setCount(section: Section) {
  return section.properties.filter((property) => !isBlank(readProperty(props.config, property.key))).length
}

function severityOf(section: Section) {
  const found = section.properties.flatMap((property) => problemsOf(property.key))
  if (found.some((problem) => problem.severity === 'error')) return 'error'
  return found.length ? 'warning' : undefined
}

// Groups open when they hold something; afterwards they stay as the user leaves them
const expanded = ref(new Set<string>())

watch(() => props.component, () => {
  expanded.value = new Set(sections.value
    .filter((section) => section.title && (setCount(section) > 0 || severityOf(section)))
    .map((section) => section.title))
}, { immediate: true })

function toggle(title: string) {
  const next = new Set(expanded.value)
  if (next.has(title)) next.delete(title)
  else next.add(title)
  expanded.value = next
}

function change(property: ConfigProperty, value: unknown) {
  emit('change', writeProperty(props.config, property, value))
}

function preview(path: string[]) {
  const value = path.reduce<unknown>((target, key) => (target as Record<string, unknown>)?.[key], props.config)
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

function remove(path: string[]) {
  if (props.config) emit('change', removeKey(props.config, path))
}
</script>

<template>
  <div v-if="info" class="config-form" data-cy="config-form">
    <p v-if="!info.properties.length" class="muted small none">This component has no configuration.</p>
    <small v-for="problem in general" :key="problem.message" class="general" :class="problem.severity">
      {{ problem.message }}
    </small>

    <template v-for="section in sections" :key="section.title">
      <div v-if="!section.title" class="fields">
        <ConfigField v-for="property in section.properties" :key="property.key" :property="property"
          :value="readProperty(config, property.key)" :problems="problemsOf(property.key)"
          @change="change(property, $event)" />
      </div>
      <section v-else class="group" :class="{ open: expanded.has(section.title) }">
        <button type="button" class="group-head" :aria-expanded="expanded.has(section.title)"
          :data-cy="`group-${section.title}`" @click="toggle(section.title)">
          <i :class="expanded.has(section.title) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" />
          <span class="group-title">{{ section.title }}</span>
          <span v-if="severityOf(section)" class="dot" :class="severityOf(section)"
            :aria-label="severityOf(section) === 'error' ? 'Has errors' : 'Has warnings'" />
          <span class="spacer" />
          <span v-if="setCount(section)" class="muted small">{{ setCount(section) }} set</span>
        </button>
        <div v-if="expanded.has(section.title)" class="fields">
          <ConfigField v-for="property in section.properties" :key="property.key" :property="property"
            :value="readProperty(config, property.key)" :problems="problemsOf(property.key)"
            @change="change(property, $event)" />
        </div>
      </section>
    </template>

    <section v-if="others.length" class="others" data-cy="other-properties">
      <span class="others-title">Not properties of {{ info.label }}</span>
      <div v-for="path in others" :key="path.join('.')" class="other">
        <code class="truncate">{{ path.join('.') }}: {{ preview(path) }}</code>
        <Button icon="pi pi-trash" text rounded size="small" severity="secondary"
          :aria-label="`Remove ${path.join('.')}`" v-tooltip.left="'Remove'" @click="remove(path)" />
      </div>
      <small class="muted">LDIO ignores these keys. A typo, perhaps?</small>
    </section>
  </div>
</template>

<style scoped>
.config-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.none {
  margin: 0;
}

.general.error {
  color: var(--p-red-600);
}

.general.warning {
  color: var(--p-amber-700);
}

.fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.group {
  border: 1px solid var(--card-border);
  border-radius: 8px;
}

.group .fields {
  padding: 4px 12px 14px;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.group-head .pi {
  font-size: 10px;
  color: var(--muted);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.dot.error {
  background: var(--p-red-500);
}

.dot.warning {
  background: var(--p-amber-500);
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
