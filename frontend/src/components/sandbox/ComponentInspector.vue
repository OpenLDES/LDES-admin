<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import CodeEditor from '@/components/common/CodeEditor.vue'
import type { ComponentConfig } from '@/domain/ldio'
import {
  componentInfo,
  componentsOfKind,
  type ConfigProperty,
  configValue,
  kinds,
} from '@/pipeline/catalog'
import { cloneConfig, label } from '@/pipeline/graph'
import { componentToYaml, parseComponentYaml } from '@/pipeline/yaml'
import { useSandboxStore } from '@/stores/sandbox'

/**
 * Right-hand panel of the sandbox: the YAML of the selected component (editable, applied while
 * typing), or the whole pipeline when nothing is selected.
 */
const emit = defineEmits<{ editYaml: [] }>()

const sandbox = useSandboxStore()
const toast = useToast()
const node = computed(() => sandbox.selected)
const info = computed(() => node.value ? componentInfo(node.value.data.component) : undefined)
const kind = computed(() => node.value ? kinds[node.value.data.kind] : undefined)
const issues = computed(() => node.value ? sandbox.issuesOf(node.value.id) : [])
const alternatives = computed(() => node.value
  ? componentsOfKind(node.value.data.kind).map((c) => ({ label: c.label, value: c.id }))
  : [])

const draft = ref('')
const parseError = ref<string>()
let timer: ReturnType<typeof setTimeout> | undefined

function resetDraft() {
  draft.value = node.value ? componentToYaml(node.value.data.component, node.value.data.config) : ''
  parseError.value = undefined
}

watch(() => node.value?.id, resetDraft, { immediate: true })

function onDraft(text: string) {
  draft.value = text
  clearTimeout(timer)
  timer = setTimeout(apply, 250)
}

function apply() {
  const current = node.value
  if (!current) return
  const parsed = parseComponentYaml(draft.value, current.data.kind)
  if (!parsed.ok) {
    parseError.value = parsed.error
    return
  }
  parseError.value = undefined
  sandbox.updateComponent(current.id, parsed.name, parsed.config)
}

function swap(component: string) {
  if (!node.value) return
  sandbox.updateComponent(node.value.id, component, cloneConfig(componentInfo(component)!.defaultConfig))
  resetDraft()
}

function isSet(property: ConfigProperty) {
  const value = configValue(node.value?.data.config, property.key)
  return value !== undefined && value !== null && value !== ''
}

/** Adds a property to the config, nested along its dots, with its default value */
function addProperty(property: ConfigProperty) {
  if (!node.value || isSet(property)) return
  const config: ComponentConfig = cloneConfig(node.value.data.config)
  const path = property.key.split('.')
  let target = config
  path.slice(0, -1).forEach((segment) => {
    if (typeof target[segment] !== 'object' || target[segment] === null) target[segment] = {}
    target = target[segment] as ComponentConfig
  })
  target[path[path.length - 1]] = property.default ?? ''
  sandbox.updateComponent(node.value.id, node.value.data.component, config)
  resetDraft()
}

async function copyYaml() {
  await navigator.clipboard?.writeText(sandbox.yaml)
  toast.add({ severity: 'info', summary: 'Pipeline YAML copied', life: 2500 })
}

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="inspector" data-cy="inspector">
    <template v-if="node && info && kind">
      <header class="header" :style="{ '--kind': kind.color }">
        <div class="header-icon"><i :class="info.icon" /></div>
        <div class="header-text">
          <span class="header-kind">{{ kind.label }}</span>
          <h2>{{ info.label }}</h2>
        </div>
        <Button icon="pi pi-times" text rounded severity="secondary" size="small" aria-label="Close"
          @click="sandbox.selectedId = undefined" />
      </header>
      <div class="content">
        <p class="muted small description">
          {{ info.description }}.
          <a :href="info.docs" target="_blank" rel="noopener">Documentation <i class="pi pi-external-link" /></a>
        </p>

        <div class="field">
          <label class="label" for="inspector-component">Component</label>
          <Select :model-value="node.data.component" input-id="inspector-component" :options="alternatives"
            option-label="label" option-value="value" fluid size="small" @update:model-value="swap" />
        </div>

        <div class="field">
          <div class="row">
            <span class="label">Configuration (YAML)</span>
            <span class="spacer" />
            <span v-if="!parseError" class="applied small"><i class="pi pi-check" /> Applied</span>
          </div>
          <CodeEditor :model-value="draft" language="yaml" min-height="150px" max-height="340px"
            data-cy="component-yaml" @update:model-value="onDraft" />
          <Message v-if="parseError" severity="error" size="small" :closable="false" data-cy="yaml-error">
            {{ parseError }}
          </Message>
        </div>

        <Message v-for="issue in issues" :key="issue.message" :severity="issue.severity === 'error' ? 'error' : 'warn'"
          size="small" :closable="false">{{ issue.message }}</Message>

        <div v-if="info.properties.length" class="field">
          <span class="label">Properties</span>
          <ul class="properties">
            <li v-for="property in info.properties" :key="property.key" class="property"
              :class="{ set: isSet(property) }">
              <div class="property-head">
                <code>{{ property.key }}</code>
                <span v-if="property.required" class="required">required</span>
                <span class="spacer" />
                <Button v-if="!isSet(property)" icon="pi pi-plus" text rounded size="small"
                  :aria-label="`Add ${property.key}`" v-tooltip.left="'Add to the configuration'"
                  @click="addProperty(property)" />
                <i v-else class="pi pi-check set-icon" />
              </div>
              <div class="muted small">{{ property.description }}</div>
              <div v-if="property.default" class="muted small">Default: <code>{{ property.default }}</code></div>
            </li>
          </ul>
        </div>
        <p v-else class="muted small">This component has no configuration.</p>

        <div class="node-actions">
          <Button label="Duplicate" icon="pi pi-copy" severity="secondary" text size="small"
            @click="sandbox.duplicate(node.id)" />
          <Button label="Remove" icon="pi pi-trash" severity="danger" text size="small" data-cy="remove-node"
            @click="sandbox.removeNode(node.id)" />
        </div>
      </div>
    </template>

    <template v-else>
      <header class="header pipeline">
        <div class="header-icon"><i class="pi pi-sitemap" /></div>
        <div class="header-text">
          <span class="header-kind">Pipeline</span>
          <h2>{{ sandbox.name || 'Untitled' }}</h2>
        </div>
      </header>
      <div class="content">
        <div class="field">
          <label class="label" for="pipeline-name">Name</label>
          <InputText id="pipeline-name" v-model="sandbox.name" fluid size="small" data-cy="pipeline-name" />
        </div>
        <div class="field">
          <label class="label" for="pipeline-description">Description</label>
          <Textarea id="pipeline-description" v-model="sandbox.description" rows="2" auto-resize fluid />
        </div>

        <div class="field">
          <span class="label">Validation</span>
          <div v-if="!sandbox.conversion.issues.length" class="valid">
            <i class="pi pi-check-circle" /> A valid LDIO pipeline
          </div>
          <button v-for="issue in sandbox.conversion.issues" :key="issue.message" type="button" class="issue"
            :class="issue.severity" :disabled="!issue.nodeId" @click="sandbox.selectedId = issue.nodeId">
            <i :class="issue.severity === 'error' ? 'pi pi-times-circle' : 'pi pi-exclamation-triangle'" />
            <span>{{ issue.message }}</span>
          </button>
        </div>

        <div class="field">
          <div class="row">
            <span class="label">Pipeline YAML</span>
            <span class="spacer" />
            <Button icon="pi pi-copy" text rounded size="small" aria-label="Copy YAML" v-tooltip.left="'Copy'"
              @click="copyYaml" />
            <Button label="Edit" icon="pi pi-pencil" text size="small" @click="emit('editYaml')" />
          </div>
          <CodeEditor :model-value="sandbox.yaml" language="yaml" readonly min-height="120px" max-height="420px"
            data-cy="pipeline-yaml" />
          <p class="muted small hint">
            <i class="pi pi-lightbulb" /> Select a component on the canvas to see and edit its part of the
            pipeline.
          </p>
        </div>
        <div class="selectable">
          <span class="label">Components</span>
          <button v-for="item in sandbox.nodes" :key="item.id" type="button" class="component-link"
            :style="{ '--kind': kinds[item.data.kind].color }" @click="sandbox.selectedId = item.id">
            <span class="kind-dot" :style="{ background: kinds[item.data.kind].color }" />
            {{ label(item) }}
            <span class="muted small">{{ kinds[item.data.kind].label }}</span>
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.inspector {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--card-border);
  border-top: 3px solid var(--kind, var(--p-primary-color));
}

.header-icon {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--kind, var(--p-primary-color)) 14%, transparent);
  color: var(--kind, var(--p-primary-color));
}

.header-text {
  flex: 1;
  min-width: 0;
}

.header-kind {
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--kind, var(--p-primary-color));
}

.header h2 {
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.description {
  margin: 0;
  line-height: 1.5;
}

.description .pi {
  font-size: 9px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.applied {
  color: var(--p-green-600);
}

.applied .pi {
  font-size: 10px;
}

.properties {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--card-border);
  border-radius: 8px;
}

.property {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.property + .property {
  border-top: 1px solid var(--card-border);
}

.property-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
}

.property-head code {
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

.set-icon {
  font-size: 11px;
  color: var(--p-green-600);
  margin-right: 8px;
}

.node-actions {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--card-border);
  padding-top: 8px;
}

.valid {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--p-green-700);
  background: var(--p-green-50);
}

.app-dark .valid {
  color: var(--p-green-300);
  background: color-mix(in srgb, var(--p-green-500) 12%, transparent);
}

.issue {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: none;
  font: inherit;
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}

.issue:disabled {
  cursor: default;
}

.issue .pi {
  margin-top: 2px;
  font-size: 12px;
}

.issue.error {
  color: var(--p-red-700);
  background: var(--p-red-50);
}

.issue.warning {
  color: var(--p-amber-800);
  background: var(--p-amber-50);
}

.app-dark .issue.error {
  color: var(--p-red-300);
  background: color-mix(in srgb, var(--p-red-500) 12%, transparent);
}

.app-dark .issue.warning {
  color: var(--p-amber-300);
  background: color-mix(in srgb, var(--p-amber-500) 12%, transparent);
}

.hint {
  margin: 0;
}

.selectable {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.component-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: none;
  border-radius: 6px;
  background: none;
  color: inherit;
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.component-link:hover {
  background: var(--hover-bg);
}

.component-link .muted {
  margin-left: auto;
}
</style>
