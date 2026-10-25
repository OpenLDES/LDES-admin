<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import { useTurtleDraft } from '@/composables/useTurtleDraft'
import type { EventStream, View } from '@/domain/ldes'
import { PREFIXES } from '@/domain/rdf'
import {
  fragmentationKinds,
  retentionPolicyKinds,
  type StreamContext,
  VIEW_DOCS,
  viewDescriptionProperties,
} from '@/ldes/catalog'
import { hasErrors, kindOf } from '@/ldes/definition'
import { setObjects } from '@/ldes/turtle'
import {
  addFragmentation,
  addViewRetentionPolicy,
  descriptionOf,
  fragmentationsOf,
  moveFragmentation,
  removeFragmentation,
  removeViewRetentionPolicy,
  renameView,
  viewNameOf,
  viewOf,
  type ViewPreset,
  viewPresets,
  viewProblems,
  viewRetentionPolicies,
  viewTemplate,
  writeDescriptionValue,
} from '@/ldes/view'
import DefinitionEditor from './DefinitionEditor.vue'
import DefinitionFields from './DefinitionFields.vue'
import ResourceCard from './ResourceCard.vue'

/**
 * Creates a view on an event stream: its definition as a form and as Turtle, both editable and
 * kept in step, and validated as the LDES server would. Presets give a head start.
 */
const props = defineProps<{ serverId: string; serverUrl: string; stream: EventStream }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ created: [view: View] }>()

const { graph, draft, parseError, change, edit, apply } = useTurtleDraft()
const busy = ref(false)
const error = ref<string>()
const preset = ref<ViewPreset>(viewPresets[0])

const context = computed<StreamContext>(() => ({
  timestampPath: props.stream.timestampPath,
  versionOfPath: props.stream.versionOfPath,
}))
const view = computed(() => viewOf(graph.value))
const description = computed(() => descriptionOf(graph.value))
const fragmentations = computed(() => fragmentationsOf(graph.value))
const policies = computed(() => viewRetentionPolicies(graph.value))
const problems = computed(() => viewProblems(graph.value))
const nameProblem = computed(() => problems.value.find((problem) => problem.key === 'name'))
const blocked = computed(() => !!parseError.value || hasErrors(problems.value))

/** Predicates of the view description that have a section of their own */
const sections = [`${PREFIXES.tree}fragmentationStrategy`, `${PREFIXES.ldes}retentionPolicy`]

function start(from: ViewPreset) {
  preset.value = from
  change(viewTemplate(from, `${props.serverUrl}/${props.stream.collection}`, context.value))
}

watch(visible, (open) => {
  if (open) {
    start(viewPresets[0])
    error.value = undefined
  }
}, { immediate: true })

function hasPolicy(type: string) {
  return policies.value.some((policy) => kindOf(graph.value, policy, retentionPolicyKinds)?.type === type)
}

async function submit() {
  apply()
  if (blocked.value) return
  busy.value = true
  error.value = undefined
  try {
    const created = await api.createView(props.serverId, props.stream.collection, draft.value)
    visible.value = false
    emit('created', created)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="`New view on ${stream.collection}`"
    :style="{ width: '1100px', maxWidth: '96vw' }" data-cy="create-view-dialog">
    <div class="presets">
      <span class="label">Start from</span>
      <div class="preset-list">
        <button v-for="option in viewPresets" :key="option.id" type="button" class="preset"
          :class="{ selected: preset.id === option.id }" :data-cy="`preset-${option.id}`" @click="start(option)">
          <i :class="option.icon" />
          <span class="preset-title">{{ option.title }}</span>
          <span class="muted small">{{ option.description }}</span>
        </button>
      </div>
    </div>

    <div class="layout">
      <div class="form">
        <Message v-if="!view || !description" severity="warn" :closable="false">
          The definition has no view: an IRI with a <code>tree:viewDescription</code> that holds its
          configuration.
        </Message>
        <template v-else>
          <div class="name-field">
            <label for="view-name">View name</label>
            <InputText id="view-name" :model-value="viewNameOf(graph)" :invalid="!!nameProblem" fluid
              spellcheck="false" data-cy="view-name" @update:model-value="change(renameView(graph, $event ?? ''))" />
            <small v-if="nameProblem" class="problem"><i class="pi pi-times-circle" /> {{ nameProblem.message }}</small>
            <small v-else class="muted">Part of every URL of the view: {{ view.value }}</small>
          </div>

          <DefinitionFields :graph="graph" :node="description" :properties="viewDescriptionProperties"
            owner="a view description" :others="sections" :problems="problems"
            @change="(property, value) => change(writeDescriptionValue(graph, property, value))"
            @remove="(predicate) => change(setObjects(graph, description!, predicate, []))" />

          <section class="section" data-cy="fragmentations">
            <div class="section-head">
              <span class="section-title">Fragmentations</span>
              <span class="muted small">The first one makes the top-level buckets, each next one divides those
                further. Every bucket is paginated; without fragmentations, the view is just pages.</span>
            </div>
            <template v-if="fragmentations">
              <ResourceCard v-for="(fragmentation, index) in fragmentations" :key="fragmentation.value" :graph="graph"
                :node="fragmentation" :kinds="fragmentationKinds" noun="fragmentation" :problems="problems"
                :stream="context" :position="{ index, count: fragmentations.length }"
                @change="change" @move="change(moveFragmentation(graph, index, $event))"
                @remove="change(removeFragmentation(graph, index))" />
              <div class="add-row">
                <Button v-for="kind in fragmentationKinds" :key="kind.type" :label="`Add ${kind.label.toLowerCase()}`"
                  icon="pi pi-plus" text size="small" :data-cy="`add-fragmentation-${kind.label.toLowerCase()}`"
                  @click="change(addFragmentation(graph, kind, context))" />
              </div>
            </template>
          </section>

          <section class="section" data-cy="view-retention">
            <div class="section-head">
              <span class="section-title">Retention</span>
              <span class="muted small">Members leave the view once a policy no longer keeps them. Without a
                policy, the view keeps every member.</span>
            </div>
            <ResourceCard v-for="policy in policies" :key="policy.value" :graph="graph" :node="policy"
              :kinds="retentionPolicyKinds" noun="retention policy" :problems="problems"
              @change="change" @remove="change(removeViewRetentionPolicy(graph, policy))" />
            <div class="add-row">
              <Button v-for="kind in retentionPolicyKinds" :key="kind.type" :label="`Add ${kind.label.toLowerCase()} retention`"
                icon="pi pi-plus" text size="small" :disabled="hasPolicy(kind.type)"
                :data-cy="`add-retention-${kind.label.toLowerCase()}`"
                @click="change(addViewRetentionPolicy(graph, kind))" />
            </div>
          </section>

          <a :href="VIEW_DOCS" target="_blank" rel="noopener" class="small docs">
            Configuring a view <i class="pi pi-external-link" />
          </a>
        </template>
      </div>

      <DefinitionEditor class="definition" :draft="draft" :parse-error="parseError"
        :problems="problems.filter((problem) => !problem.node)" @edit="edit" @reset="start(preset)" />
    </div>
    <Message v-if="error" severity="error" :closable="false" class="error">{{ error }}</Message>
    <template #footer>
      <span v-if="blocked" class="muted small blocked">Fix the errors to create the view</span>
      <Button label="Cancel" severity="secondary" text @click="visible = false" />
      <Button label="Create view" icon="pi pi-plus" :loading="busy" :disabled="blocked" data-cy="create-view-submit"
        @click="submit" />
    </template>
  </Dialog>
</template>

<style scoped>
.presets {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}

.preset-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

@media (max-width: 900px) {
  .preset-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.preset {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.preset .pi {
  color: var(--p-primary-color);
  margin-bottom: 4px;
}

.preset.selected {
  border-color: var(--p-primary-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--p-primary-500) 15%, transparent);
}

.preset-title {
  font-weight: 600;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

.form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.definition {
  position: sticky;
  top: 0;
}

.name-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.name-field label {
  font-weight: 500;
}

.name-field .muted {
  overflow-wrap: anywhere;
}

.problem {
  color: var(--p-red-600);
}

.app-dark .problem {
  color: var(--p-red-300);
}

.problem .pi {
  font-size: 11px;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.section-title {
  font-size: 13.5px;
  font-weight: 600;
}

.add-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.add-row :deep(.p-button) {
  padding-left: 4px;
}

.docs {
  align-self: flex-start;
}

.docs .pi {
  font-size: 9px;
}

.error {
  margin-top: 14px;
}

.blocked {
  margin-right: auto;
}
</style>
