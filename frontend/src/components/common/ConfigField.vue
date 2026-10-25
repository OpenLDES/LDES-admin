<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { stringify } from 'yaml'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import type { ConfigProblem, ConfigProperty, SelectOption } from '@/pipeline/catalog'
import { asBoolean, asHeaders, asList, type HttpHeader, isBlank } from '@/pipeline/config'

/**
 * Edits one property of a configuration (of an LDIO component, or in the definition of an event
 * stream or view), with the input that fits its type, and shows what is wrong with it.
 */
const props = defineProps<{
  property: ConfigProperty
  value: unknown
  problems: ConfigProblem[]
  /** Tells apart the inputs of fields with the same key in one form, e.g. of two fragmentations */
  scope?: string
}>()
const emit = defineEmits<{ change: [value: unknown] }>()

const id = computed(() => `config-${props.scope ? `${props.scope}-` : ''}${props.property.key.replace(/[^\w-]/g, '-')}`)
const set = computed(() => !isBlank(props.value))
const text = computed(() => (isBlank(props.value) || typeof props.value === 'object' ? '' : String(props.value)))
const placeholder = computed(() => props.property.default ?? props.property.example ?? '')
const fieldProblems = computed(() => props.problems.filter((problem) => problem.index === undefined))
const invalid = computed(() => fieldProblems.value.some((problem) => problem.severity === 'error'))
const monospace = computed(() => ['url', 'iri', 'propertyPath', 'duration', 'cron', 'integer']
  .includes(props.property.type))

// Booleans: unset shows the default
const checked = computed(() => asBoolean(props.value) ?? asBoolean(props.property.default) ?? false)

// Selects: the label and the value, so the YAML value is never a surprise; a value that is no
// option stays visible, next to what is wrong with it
const options = computed(() => (text.value && !props.property.custom
  && !props.property.options?.some((option) => option.value === text.value)
  ? [...(props.property.options ?? []), { value: text.value }]
  : props.property.options))

function optionLabel(option: SelectOption) {
  return option.label && !props.property.custom ? `${option.label} — ${option.value}` : option.value
}

// Lists ---------------------------------------------------------------------------------------

const items = computed(() => asList(props.value))
const urlPlaceholder = 'https://example.org/ldes'
const headers = computed(() => asHeaders(props.value))
const listEl = ref<HTMLElement>()

function itemProblems(index: number) {
  return props.problems.filter((problem) => problem.index === index)
}

function itemInvalid(index: number) {
  return itemProblems(index).some((problem) => problem.severity === 'error')
}

async function focusLast() {
  await nextTick()
  const inputs = listEl.value?.querySelectorAll('input')
  inputs?.[inputs.length - (props.property.type === 'headers' ? 2 : 1)]?.focus()
}

function setItem(index: number, value: string | undefined) {
  emit('change', items.value.map((item, i) => (i === index ? value ?? '' : item)))
}

function addItem() {
  emit('change', [...items.value, ''])
  focusLast()
}

function removeItem(index: number) {
  emit('change', items.value.filter((_, i) => i !== index))
}

function setHeader(index: number, part: keyof HttpHeader, value: string | undefined) {
  emit('change', headers.value.map((header, i) => (i === index ? { ...header, [part]: value ?? '' } : header)))
}

function addHeader() {
  emit('change', [...headers.value, { key: '', value: '' }])
  focusLast()
}

function removeHeader(index: number) {
  emit('change', headers.value.filter((_, i) => i !== index))
}

const mapPreview = computed(() => (set.value && typeof props.value === 'object'
  ? stringify(props.value, { indent: 2 }).trim() : ''))
</script>

<template>
  <div class="config-field" :class="{ set, invalid }" :data-cy="`config-${property.key}`">
    <div class="field-head">
      <label :for="id"><code>{{ property.key }}</code></label>
      <span v-if="property.required" class="required">required</span>
      <span class="spacer" />
      <Button v-if="set && !property.required && property.type !== 'map'" icon="pi pi-undo" text rounded
        size="small" severity="secondary" class="reset"
        v-tooltip.left="property.default ? `Back to the default, ${property.default}` : 'Remove from the configuration'"
        :aria-label="`Remove ${property.key}`" data-cy="reset-property" @click="emit('change', undefined)" />
    </div>

    <div v-if="property.type === 'boolean'" class="toggle-row">
      <ToggleSwitch :input-id="id" :model-value="checked" :invalid="invalid"
        @update:model-value="emit('change', $event)" />
      <span class="mono small">{{ checked }}<span v-if="!set" class="muted"> (default)</span></span>
    </div>

    <Select v-else-if="property.type === 'select'" :input-id="id" :model-value="text || undefined"
      :options="options" :option-label="optionLabel" option-value="value" :editable="property.custom"
      :placeholder="property.default ? `Default: ${property.default}` : 'Choose…'"
      :invalid="invalid" fluid size="small"
      @update:model-value="emit('change', $event ?? undefined)">
      <template #option="{ option }">
        <span class="option">
          <span>{{ option.label ?? option.value }}</span>
          <code v-if="option.label" class="muted">{{ option.value }}</code>
        </span>
      </template>
    </Select>

    <div v-else-if="property.type === 'urls'" ref="listEl" class="list">
      <div v-for="(url, index) in items" :key="index" class="list-item">
        <div class="list-row">
          <InputText :model-value="url" :invalid="itemInvalid(index)" :placeholder="urlPlaceholder"
            :aria-label="`${property.key}, line ${index + 1}`" fluid size="small" class="mono" spellcheck="false"
            data-cy="list-item" @update:model-value="setItem(index, $event)" />
          <Button icon="pi pi-trash" text rounded size="small" severity="secondary"
            :aria-label="`Remove line ${index + 1}`" data-cy="remove-item" @click="removeItem(index)" />
        </div>
        <small v-for="problem in itemProblems(index)" :key="problem.message" class="problem" :class="problem.severity">
          <i :class="problem.severity === 'error' ? 'pi pi-times-circle' : 'pi pi-exclamation-triangle'" />
          {{ problem.message }}
        </small>
      </div>
      <Button :label="items.length ? 'Add another URL' : 'Add a URL'" icon="pi pi-plus" text size="small"
        class="add" data-cy="add-item" @click="addItem" />
    </div>

    <Password v-else-if="property.type === 'secret'" :input-id="id" :model-value="text" :feedback="false"
      toggle-mask fluid :invalid="invalid" :placeholder="placeholder" class="secret"
      @update:model-value="emit('change', $event)" />

    <Textarea v-else-if="['text', 'json', 'context'].includes(property.type)" :id="id" :model-value="text"
      :placeholder="placeholder" :invalid="invalid" auto-resize rows="2" fluid class="mono code-area"
      spellcheck="false" @update:model-value="emit('change', $event)" />

    <div v-else-if="property.type === 'headers'" ref="listEl" class="list">
      <div v-for="(header, index) in headers" :key="index" class="list-item">
        <div class="list-row">
          <InputText :model-value="header.key" placeholder="Name" :invalid="itemInvalid(index) && !header.key.trim()"
            :aria-label="`Header ${index + 1}, name`" size="small" class="header-name"
            @update:model-value="setHeader(index, 'key', $event)" />
          <InputText :model-value="header.value" placeholder="Value"
            :invalid="itemInvalid(index) && !header.value.trim()" :aria-label="`Header ${index + 1}, value`"
            size="small" fluid @update:model-value="setHeader(index, 'value', $event)" />
          <Button icon="pi pi-trash" text rounded size="small" severity="secondary"
            :aria-label="`Remove header ${index + 1}`" @click="removeHeader(index)" />
        </div>
        <small v-for="problem in itemProblems(index)" :key="problem.message" class="problem" :class="problem.severity">
          <i class="pi pi-times-circle" /> {{ problem.message }}
        </small>
      </div>
      <Button label="Add a header" icon="pi pi-plus" text size="small" class="add" @click="addHeader" />
    </div>

    <div v-else-if="property.type === 'map'" class="map">
      <pre v-if="mapPreview" class="mono small">{{ mapPreview }}</pre>
      <small class="muted">Edit this nested configuration in the YAML below.</small>
    </div>

    <InputText v-else :id="id" :model-value="text" :placeholder="placeholder" :invalid="invalid"
      :inputmode="property.type === 'integer' ? 'numeric' : undefined" fluid size="small"
      :class="{ mono: monospace }" spellcheck="false" @update:model-value="emit('change', $event)" />

    <small class="help">
      {{ property.description }}<template v-if="property.default && property.type !== 'boolean'">. Default: <code>{{ property.default }}</code></template>
    </small>
    <small v-for="problem in fieldProblems" :key="problem.message" class="problem" :class="problem.severity"
      data-cy="property-problem">
      <i :class="problem.severity === 'error' ? 'pi pi-times-circle' : 'pi pi-exclamation-triangle'" />
      {{ problem.message }}
    </small>
  </div>
</template>

<style scoped>
.config-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.field-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.field-head code {
  font-size: 12px;
  font-weight: 600;
}

.required {
  font-size: 10.5px;
  font-weight: 600;
  padding: 0 6px;
  border-radius: 4px;
  color: var(--p-red-700);
  background: var(--p-red-50);
}

.app-dark .required {
  color: var(--p-red-300);
  background: color-mix(in srgb, var(--p-red-500) 14%, transparent);
}

.reset {
  width: 24px !important;
  height: 24px !important;
}

.toggle-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mono,
.mono :deep(input) {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.code-area {
  font-size: 12px;
  line-height: 1.5;
}

.secret :deep(input) {
  font-size: 13px;
  padding-top: 6px;
  padding-bottom: 6px;
}

.option {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.option code {
  font-size: 11.5px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.list-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.list-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-name {
  width: 40%;
  flex: none;
}

.add {
  align-self: flex-start;
  padding-left: 4px !important;
}

.map pre {
  margin: 0 0 4px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--p-content-hover-background);
  white-space: pre-wrap;
}

.help {
  color: var(--muted);
  line-height: 1.45;
}

.help code {
  font-size: 11.5px;
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
</style>
