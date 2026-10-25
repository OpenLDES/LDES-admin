<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import { api } from '@/api'
import ExampleSteps from '@/components/examples/ExampleSteps.vue'
import { errorMessage } from '@/composables/useAsync'
import { useTurtleDraft } from '@/composables/useTurtleDraft'
import type { EventStream } from '@/domain/ldes'
import { PREFIXES } from '@/domain/rdf'
import { exampleById, examplePipelineName, examples } from '@/examples'
import { failed, type InstallStep, installExample, plannedSteps } from '@/examples/install'
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
import { parseTurtle, setObjects } from '@/ldes/turtle'
import { useInstancesStore } from '@/stores/instances'
import DefinitionEditor from './DefinitionEditor.vue'
import DefinitionFields from './DefinitionFields.vue'
import ResourceCard from './ResourceCard.vue'

/**
 * Creates an event stream: its definition as a form and as Turtle, both editable and kept in step,
 * and validated as the LDES server would. Starting from an example, it can create the views, the
 * pipeline and the sample data of the example too.
 */
const props = defineProps<{ serverId: string; serverUrl: string }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ created: [stream: EventStream] }>()

const instances = useInstancesStore()
const { graph, draft, parseError, change, edit, apply } = useTurtleDraft()
const busy = ref(false)
const error = ref<string>()
const kafkaOpen = ref(false)

/** An empty event stream, or one of the examples */
const templates = [
  { id: 'empty', title: 'Empty event stream', description: 'The paths and a shape to fill in', icon: 'pi pi-file' },
  ...examples.map(({ id, title, description, icon }) => ({ id, title, description, icon })),
]
const template = ref('empty')
const example = computed(() => exampleById(template.value))
/** What to create of the example, besides the event stream: nothing unless the user asks */
const extras = ref({ views: false, pipeline: false, data: false })
const ldioId = ref<string>()
const exampleSteps = ref<InstallStep[]>([])
/** Created, but not every part of the example could be: the dialog stays open to tell */
const created = ref<EventStream>()

const server = computed(() => instances.find(props.serverId))
/** The LDIOs that reach the server: for a local server, the local ones on its Docker network */
const ldios = computed(() => instances.ldios
  .filter((ldio) => server.value?.origin !== 'LOCAL' || ldio.origin === 'LOCAL')
  .sort((a, b) => Number(b.sandbox) - Number(a.sandbox)))
const ldio = computed(() => ldios.value.find((candidate) => candidate.id === ldioId.value))

const stream = computed(() => streamOf(graph.value))
const problems = computed(() => eventStreamProblems(graph.value))
const collectionProblem = computed(() => problems.value.find((problem) => problem.key === 'collection'))
const fieldProblems = computed(() => problems.value.filter((problem) => problem.key !== 'collection'))
const policies = computed(() => eventSourcePolicies(graph.value))
const kafka = computed(() => kafkaSourceOf(graph.value))
const blocked = computed(() => !!parseError.value || hasErrors(problems.value))

/** Predicates of the event stream that have a section of their own */
const sections = [`${PREFIXES.ldes}eventSource`, `${PREFIXES.ldes}kafkaSource`, `${PREFIXES.tree}shape`]

// The instances may still be loading when the dialog opens
watch(ldios, (list) => {
  if (!list.some((candidate) => candidate.id === ldioId.value)) ldioId.value = list[0]?.id
})

function start(id: string) {
  template.value = id
  reset()
}

function reset() {
  const chosen = example.value
  const parsed = chosen && parseTurtle(chosen.eventStream(props.serverUrl, chosen.collection))
  change(parsed?.ok ? parsed.graph : eventStreamTemplate(props.serverUrl, 'my-collection'))
  kafkaOpen.value = false
}

watch(visible, (open) => {
  if (open) {
    template.value = 'empty'
    extras.value = { views: false, pipeline: false, data: false }
    ldioId.value = ldios.value[0]?.id
    exampleSteps.value = []
    created.value = undefined
    reset()
    error.value = undefined
  } else if (created.value) {
    emit('created', created.value)
    created.value = undefined
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
    const newStream = await api.createEventStream(props.serverId, draft.value, 'text/turtle')
    if (await createExtras(newStream)) {
      visible.value = false
      emit('created', newStream)
    } else {
      created.value = newStream
      error.value = `Event stream ${newStream.collection} is created, but not everything of the example.`
    }
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

/** Creates what the user asked of the example; false when that failed */
async function createExtras(newStream: EventStream): Promise<boolean> {
  const { views, pipeline, data } = extras.value
  if (!example.value || !server.value || !(views || pipeline || data)) return true
  const request = {
    example: example.value,
    server: server.value,
    ldio: pipeline ? ldio.value : undefined,
    collection: newStream.collection,
    parts: { stream: false, views, pipeline, data: data && !!example.value.sampleData },
  }
  exampleSteps.value = plannedSteps(request)
  const steps = await installExample(request, { onChange: (updated) => { exampleSteps.value = updated } })
  return !failed(steps)
}
</script>

<template>
  <Dialog v-model:visible="visible" modal header="New event stream" :style="{ width: '1100px', maxWidth: '96vw' }"
    data-cy="create-stream-dialog">
    <div class="presets">
      <span class="label">Start from</span>
      <div class="preset-list">
        <button v-for="option in templates" :key="option.id" type="button" class="preset"
          :class="{ selected: template === option.id }" :disabled="busy || !!created"
          :data-cy="`stream-template-${option.id}`" @click="start(option.id)">
          <i :class="option.icon" />
          <span class="preset-title">{{ option.title }}</span>
          <span class="muted small">{{ option.description }}</span>
        </button>
      </div>
    </div>

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

          <section v-if="example" class="section extras" data-cy="example-extras">
            <div class="section-head">
              <span class="section-title">Also create</span>
              <span class="muted small">The rest of the {{ example.title.toLowerCase() }} example, for the event
                stream {{ collectionOf(graph) }}</span>
            </div>
            <div class="checkbox">
              <Checkbox v-model="extras.views" input-id="extra-views" binary :disabled="busy || !!created"
                data-cy="extra-views" />
              <label for="extra-views">The views by-page and by-time</label>
            </div>
            <div class="checkbox">
              <Checkbox v-model="extras.pipeline" input-id="extra-pipeline" binary
                :disabled="busy || !!created || !ldios.length" data-cy="extra-pipeline" />
              <label for="extra-pipeline">The pipeline <code>{{ examplePipelineName(collectionOf(graph) ?? '') }}</code>
                <template v-if="!ldios.length"> (no LDIO to run it)</template></label>
            </div>
            <Select v-if="extras.pipeline" v-model="ldioId" :options="ldios" option-label="name" option-value="id"
              aria-label="LDIO for the pipeline" class="extra-ldio" :disabled="busy || !!created"
              data-cy="extra-ldio" />
            <div v-if="example.sampleData" class="checkbox">
              <Checkbox v-model="extras.data" input-id="extra-data" binary :disabled="busy || !!created"
                data-cy="extra-data" />
              <label for="extra-data">Sample data: {{ example.sampleData.description }},
                {{ extras.pipeline ? 'sent through the pipeline' : 'ingested directly' }}</label>
            </div>
            <ExampleSteps v-if="exampleSteps.length" :groups="[{ title: example.title, icon: example.icon, steps: exampleSteps }]" />
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
      <Button v-if="created" label="Close" data-cy="close-stream-dialog" @click="visible = false" />
      <template v-else>
        <span v-if="blocked" class="muted small blocked">Fix the errors to create the event stream</span>
        <Button label="Cancel" severity="secondary" text @click="visible = false" />
        <Button label="Create event stream" icon="pi pi-plus" :loading="busy" :disabled="blocked || (extras.pipeline && !ldio)"
          data-cy="create-stream-submit" @click="submit" />
      </template>
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
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

.checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
}

.extra-ldio {
  margin-left: 28px;
  max-width: 320px;
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
