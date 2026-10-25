<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import { useTurtleDraft } from '@/composables/useTurtleDraft'
import type { EventStream } from '@/domain/ldes'
import { PREFIXES } from '@/domain/rdf'
import {
  EVENT_STREAM_DOCS,
  eventStreamProperties,
  kafkaSourceProperties,
  retentionPolicyKinds,
} from '@/ldes/catalog'
import { hasErrors, kindOf } from '@/ldes/definition'
import {
  addEventSourcePolicy,
  collectionOf,
  eventSourcePolicies,
  eventStreamProblems,
  eventStreamTemplate,
  kafkaSourceOf,
  removeEventSourcePolicy,
  renameCollection,
  streamOf,
  writeKafkaValue,
  writeStreamValue,
} from '@/ldes/eventStream'
import { setObjects } from '@/ldes/turtle'
import DefinitionEditor from './DefinitionEditor.vue'
import DefinitionFields from './DefinitionFields.vue'
import ResourceCard from './ResourceCard.vue'

/**
 * Creates an event stream: its definition as a form and as Turtle, both editable and kept in step,
 * and validated as the LDES server would.
 */
const props = defineProps<{ serverId: string; serverUrl: string }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ created: [stream: EventStream] }>()

const { graph, draft, parseError, change, edit, apply } = useTurtleDraft()
const busy = ref(false)
const error = ref<string>()
const kafkaOpen = ref(false)

const stream = computed(() => streamOf(graph.value))
const problems = computed(() => eventStreamProblems(graph.value))
const collectionProblem = computed(() => problems.value.find((problem) => problem.key === 'collection'))
const fieldProblems = computed(() => problems.value.filter((problem) => problem.key !== 'collection'))
const policies = computed(() => eventSourcePolicies(graph.value))
const kafka = computed(() => kafkaSourceOf(graph.value))
const blocked = computed(() => !!parseError.value || hasErrors(problems.value))

/** Predicates of the event stream that have a section of their own */
const sections = [`${PREFIXES.ldes}eventSource`, `${PREFIXES.ldes}kafkaSource`, `${PREFIXES.tree}shape`]

function reset() {
  change(eventStreamTemplate(props.serverUrl, 'my-collection'))
  kafkaOpen.value = false
}

watch(visible, (open) => {
  if (open) {
    reset()
    error.value = undefined
  }
}, { immediate: true })

watch(kafka, (source) => {
  if (source) kafkaOpen.value = true
})

function hasPolicy(type: string) {
  return policies.value.some((policy) => kindOf(graph.value, policy, retentionPolicyKinds)?.type === type)
}

async function submit() {
  apply()
  if (blocked.value) return
  busy.value = true
  error.value = undefined
  try {
    const created = await api.createEventStream(props.serverId, draft.value, 'text/turtle')
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
  <Dialog v-model:visible="visible" modal header="New event stream" :style="{ width: '1100px', maxWidth: '96vw' }"
    data-cy="create-stream-dialog">
    <div class="layout">
      <div class="form">
        <Message v-if="!stream" severity="warn" :closable="false">
          The definition has no event stream: an IRI of type <code>ldes:EventStream</code>.
        </Message>
        <template v-else>
          <div class="name-field" :class="{ invalid: collectionProblem }">
            <label for="stream-collection">Collection name</label>
            <InputText id="stream-collection" :model-value="collectionOf(graph)" :invalid="!!collectionProblem"
              fluid spellcheck="false" data-cy="stream-collection"
              @update:model-value="change(renameCollection(graph, $event ?? ''))" />
            <small v-if="collectionProblem" class="problem"><i class="pi pi-times-circle" /> {{ collectionProblem.message }}</small>
            <small v-else class="muted">Part of every URL of the event stream: {{ stream.value }}</small>
          </div>

          <DefinitionFields :graph="graph" :node="stream" :properties="eventStreamProperties" owner="an event stream"
            :others="sections" :problems="fieldProblems"
            @change="(property, value) => change(writeStreamValue(graph, property, value))"
            @remove="(predicate) => change(setObjects(graph, stream!, predicate, []))" />

          <section class="section" data-cy="stream-retention">
            <div class="section-head">
              <span class="section-title">Retention</span>
              <span class="muted small">Members no view holds any longer are deleted once no policy keeps them.
                Without a policy, the event stream keeps every member.</span>
            </div>
            <ResourceCard v-for="policy in policies" :key="policy.value" :graph="graph" :node="policy"
              :kinds="retentionPolicyKinds" noun="retention policy" :problems="problems"
              @change="change" @remove="change(removeEventSourcePolicy(graph, policy))" />
            <div class="add-row">
              <Button v-for="kind in retentionPolicyKinds" :key="kind.type" :label="`Add ${kind.label.toLowerCase()} retention`"
                icon="pi pi-plus" text size="small" :disabled="hasPolicy(kind.type)"
                :data-cy="`add-retention-${kind.label.toLowerCase()}`"
                @click="change(addEventSourcePolicy(graph, kind))" />
            </div>
          </section>

          <section class="section group" :class="{ open: kafkaOpen }">
            <button type="button" class="group-head" :aria-expanded="kafkaOpen" data-cy="group-kafka"
              @click="kafkaOpen = !kafkaOpen">
              <i :class="kafkaOpen ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" />
              <span class="section-title">Kafka source</span>
              <span class="spacer" />
              <span v-if="kafka" class="muted small">set</span>
            </button>
            <div v-if="kafkaOpen" class="group-body">
              <small class="muted">Reads the members from a Kafka topic. Only for servers with the Kafka ingest
                module; others answer with an error.</small>
              <DefinitionFields :graph="graph" :node="kafka" :properties="kafkaSourceProperties"
                owner="a Kafka source" :problems="problems" scope="kafka"
                @change="(property, value) => change(writeKafkaValue(graph, property, value))"
                @remove="(predicate) => change(setObjects(graph, kafka!, predicate, []))" />
            </div>
          </section>

          <a :href="EVENT_STREAM_DOCS" target="_blank" rel="noopener" class="small docs">
            Configuring an event stream <i class="pi pi-external-link" />
          </a>
        </template>
      </div>

      <DefinitionEditor class="definition" :draft="draft" :parse-error="parseError"
        :problems="problems.filter((problem) => !problem.node)" @edit="edit" @reset="reset" />
    </div>
    <Message v-if="error" severity="error" :closable="false" class="error">{{ error }}</Message>
    <template #footer>
      <span v-if="blocked" class="muted small blocked">Fix the errors to create the event stream</span>
      <Button label="Cancel" severity="secondary" text @click="visible = false" />
      <Button label="Create event stream" icon="pi pi-plus" :loading="busy" :disabled="blocked"
        data-cy="create-stream-submit" @click="submit" />
    </template>
  </Dialog>
</template>

<style scoped>
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

.group {
  border: 1px solid var(--card-border);
  border-radius: 8px;
  gap: 0;
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
  text-align: left;
  cursor: pointer;
}

.group-head .pi {
  font-size: 10px;
  color: var(--muted);
}

.group-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0 12px 14px;
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
