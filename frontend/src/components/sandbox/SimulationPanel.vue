<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Popover from 'primevue/popover'
import Select from 'primevue/select'
import CodeEditor from '@/components/common/CodeEditor.vue'
import { api } from '@/api'
import { useAction } from '@/composables/useAsync'
import type { EventStream } from '@/domain/ldes'
import { componentInfo, kinds } from '@/pipeline/catalog'
import type { SimulationStep } from '@/pipeline/simulator'
import { templateById } from '@/pipeline/templates'
import { useInstancesStore } from '@/stores/instances'
import { useSandboxStore } from '@/stores/sandbox'

/**
 * Runs a member through the pipeline and shows what every component made of it. Collapses to a
 * bar so the canvas gets the room while designing.
 */
const open = defineModel<boolean>('open', { required: true })

const sandbox = useSandboxStore()
const instances = useInstancesStore()
const { run: act, busy: ingesting } = useAction()

const contentTypes = [
  { label: 'JSON', value: 'application/json' },
  { label: 'Turtle', value: 'text/turtle' },
  { label: 'N-Quads', value: 'application/n-quads' },
  { label: 'JSON-LD', value: 'application/ld+json' },
  { label: 'CSV', value: 'text/csv' },
]

const selectedStep = ref(0)
const steps = computed(() => sandbox.result?.steps ?? [])
const shown = computed<SimulationStep | undefined>(() => steps.value[selectedStep.value])
const pending = computed(() => sandbox.conversion.order.length - steps.value.length)
const blocked = computed(() => sandbox.errors[0]?.message)

const status = computed(() => {
  if (sandbox.running) return { icon: 'pi pi-spin pi-spinner', text: 'Running…', tone: 'busy' }
  const result = sandbox.result
  if (!result) return { icon: 'pi pi-play-circle', text: 'Run a member through the pipeline to see every step', tone: 'idle' }
  const last = result.steps[result.steps.length - 1]
  if (result.delivered) {
    const outputs = result.steps.filter((step) => step.kind === 'output').length
    return { icon: 'pi pi-check-circle', text: `Delivered to ${outputs} ${outputs === 1 ? 'output' : 'outputs'}`, tone: 'ok' }
  }
  return last?.status === 'filtered'
    ? { icon: 'pi pi-filter-slash', text: `Filtered out by ${stepLabel(last)}`, tone: 'filtered' }
    : { icon: 'pi pi-times-circle', text: `Stopped at ${last ? stepLabel(last) : 'the input'}`, tone: 'error' }
})

watch(() => steps.value.length, (length) => {
  if (length) selectedStep.value = length - 1
})

const targetServer = ref<string>()
const targetCollection = ref<string>()
const streams = ref<EventStream[]>([])
const ingestPopover = ref<InstanceType<typeof Popover>>()
const serverOptions = computed(() => instances.sandboxServers.map((s) => ({ label: s.name, value: s.id })))
const collectionOptions = computed(() => streams.value.map((s) => ({ label: s.collection, value: s.collection })))

watch(serverOptions, (options) => {
  if (!targetServer.value && options.length) targetServer.value = options[0].value
}, { immediate: true })

watch(targetServer, async (id) => {
  streams.value = id ? await api.listEventStreams(id).catch(() => []) : []
  targetCollection.value = streams.value[0]?.collection
}, { immediate: true })

function runPipeline() {
  open.value = true
  sandbox.run()
}

function useTemplateSample() {
  const origin = sandbox.origin
  const template = templateById(origin.kind === 'template' ? origin.id : 'parcels-to-ldes')
  if (template) sandbox.sample = { ...template.sample }
}

async function ingest() {
  const member = sandbox.result?.member
  if (!member || !targetServer.value || !targetCollection.value) return
  const collection = targetCollection.value
  const done = await act(async () => {
    await api.ingestMember(targetServer.value!, collection, member, 'text/turtle')
    return true
  }, `Ingested the result in ${collection}`)
  if (done) ingestPopover.value?.hide()
}

function stepLabel(step: SimulationStep) {
  return componentInfo(step.component)?.label ?? step.component
}

const statusIcon: Record<SimulationStep['status'], string> = {
  ok: 'pi pi-check-circle',
  filtered: 'pi pi-filter-slash',
  error: 'pi pi-times-circle',
}
</script>

<template>
  <div class="simulation" :class="{ open }" data-cy="simulation">
    <header class="bar">
      <button type="button" class="bar-toggle" :aria-expanded="open" data-cy="toggle-simulation" @click="open = !open">
        <i :class="open ? 'pi pi-chevron-down' : 'pi pi-chevron-up'" />
        <span class="bar-title">Simulation</span>
      </button>
      <span class="bar-status" :class="status.tone"><i :class="status.icon" />{{ status.text }}</span>
      <span class="spacer" />
      <Button v-if="sandbox.result && !sandbox.running" label="Reset" severity="secondary" text size="small"
        @click="sandbox.resetSimulation()" />
      <span v-tooltip.top="blocked">
        <Button label="Run" icon="pi pi-play" size="small" :loading="sandbox.running" :disabled="!!blocked"
          data-cy="run-simulation" @click="runPipeline" />
      </span>
    </header>

    <div v-if="open" class="body">
      <section class="column">
        <header class="column-header">
          <span class="column-title"><i class="pi pi-inbox" /> <span class="truncate">Input member</span></span>
          <span class="spacer" />
          <Button icon="pi pi-refresh" severity="secondary" text rounded size="small"
            v-tooltip.top="'Use the sample member of the template'" aria-label="Sample member" @click="useTemplateSample" />
          <Select v-model="sandbox.sample.contentType" :options="contentTypes" option-label="label" option-value="value"
            size="small" aria-label="Content type" />
        </header>
        <CodeEditor v-model="sandbox.sample.content"
          :language="sandbox.sample.contentType.includes('json') ? 'json' : sandbox.sample.contentType.includes('csv') ? 'text' : 'turtle'"
          min-height="100%" class="editor" data-cy="sample-input" />
      </section>

      <section class="column steps">
        <header class="column-header">
          <span class="column-title"><i class="pi pi-list-check" /> Steps</span>
        </header>
        <ol v-if="steps.length" class="step-list">
          <li v-for="(step, index) in steps" :key="index">
            <button type="button" class="step" :class="[step.status, { selected: index === selectedStep }]"
              :style="{ '--kind': kinds[step.kind].color }" data-cy="simulation-step" @click="selectedStep = index">
              <span class="step-marker"><i :class="statusIcon[step.status]" /></span>
              <span class="step-text">
                <span class="step-title">{{ stepLabel(step) }} <span class="muted small">{{ step.durationMs }} ms</span></span>
                <span class="step-summary">{{ step.summary }}</span>
              </span>
            </button>
          </li>
          <li v-for="n in sandbox.running ? 0 : Math.max(0, pending)" :key="`pending-${n}`" class="skipped muted small">
            <i class="pi pi-minus-circle" /> Not reached
          </li>
        </ol>
        <div v-else class="placeholder muted small">
          <i class="pi pi-play-circle" />
          <p>Run the member to follow it through every component.</p>
        </div>
      </section>

      <section class="column">
        <header class="column-header">
          <span class="column-title"><i class="pi pi-file-export" />
            <span class="truncate">{{ shown ? `After ${stepLabel(shown)}` : 'Output' }}</span></span>
          <span class="spacer" />
          <Button v-if="sandbox.result?.delivered && !sandbox.running" icon="pi pi-upload" label="Ingest"
            v-tooltip.top="'Ingest the result in a sandbox LDES server'" size="small" text
            data-cy="open-ingest-result" @click="ingestPopover?.toggle($event)" />
        </header>
        <CodeEditor v-if="shown?.output" :model-value="shown.output" :language="shown.format === 'json' ? 'json' : 'turtle'"
          readonly min-height="100%" class="editor" data-cy="step-output" />
        <div v-else-if="shown" class="placeholder muted small">
          <i :class="statusIcon[shown.status]" />
          <p>{{ shown.summary }}</p>
        </div>
        <div v-else class="placeholder muted small">
          <i class="pi pi-eye" />
          <p>The model after each step shows up here.</p>
        </div>
        <Popover ref="ingestPopover">
          <div class="ingest">
            <div class="label">Ingest the result in a sandbox LDES server</div>
            <Select v-model="targetServer" :options="serverOptions" option-label="label" option-value="value"
              placeholder="Sandbox server" size="small" fluid aria-label="Sandbox LDES server" />
            <Select v-model="targetCollection" :options="collectionOptions" option-label="label" option-value="value"
              placeholder="Event stream" size="small" fluid aria-label="Event stream" />
            <Button icon="pi pi-upload" label="Ingest" size="small" :disabled="!targetCollection" :loading="ingesting"
              data-cy="ingest-result" @click="ingest" />
          </div>
        </Popover>
      </section>
    </div>
  </div>
</template>

<style scoped>
.simulation {
  display: flex;
  flex-direction: column;
  height: 46px;
  background: var(--card-bg);
  border-top: 1px solid var(--card-border);
  transition: height 0.2s ease;
}

.simulation.open {
  height: 44%;
  min-height: 260px;
}

.bar {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 46px;
  flex: none;
  padding: 0 12px 0 6px;
}

.bar-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: none;
  border-radius: 6px;
  background: none;
  color: var(--text);
  font: inherit;
  cursor: pointer;
}

.bar-toggle:hover {
  background: var(--hover-bg);
}

.bar-toggle .pi {
  font-size: 11px;
  color: var(--muted);
}

.bar-title {
  font-weight: 600;
  font-size: 13px;
}

.bar-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 12.5px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bar-status.ok {
  color: var(--p-green-600);
}

.bar-status.filtered {
  color: var(--p-amber-600);
}

.bar-status.error {
  color: var(--p-red-600);
}

.body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(200px, 0.8fr) minmax(0, 1.1fr);
  border-top: 1px solid var(--card-border);
}

.column {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  padding: 8px 12px 12px;
}

.column + .column {
  border-left: 1px solid var(--card-border);
}

.column-header {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  margin-bottom: 6px;
}

.column-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-weight: 600;
  font-size: 12.5px;
  white-space: nowrap;
}

.column-title .pi {
  color: var(--muted);
}

.editor {
  flex: 1;
  min-height: 0;
}

.editor :deep(.cm-editor) {
  height: 100%;
}

.ingest {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 260px;
}

.step-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.step {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 7px 9px;
  border: 1px solid transparent;
  border-left: 3px solid var(--kind);
  border-radius: 8px;
  background: var(--hover-bg);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  animation: appear 0.25s ease-out;
}

@keyframes appear {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
}

.step.selected {
  border-color: color-mix(in srgb, var(--kind) 50%, transparent);
  border-left-color: var(--kind);
  background: color-mix(in srgb, var(--kind) 8%, transparent);
}

.step-marker {
  margin-top: 1px;
}

.ok .step-marker {
  color: var(--p-green-600);
}

.filtered .step-marker {
  color: var(--p-amber-600);
}

.error .step-marker {
  color: var(--p-red-600);
}

.step-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.step-title {
  font-weight: 600;
  font-size: 12.5px;
}

.step-summary {
  font-size: 12px;
  color: var(--muted);
  overflow-wrap: anywhere;
}

.skipped {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
}

.placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 4px;
  padding: 16px;
  border: 1px dashed var(--card-border);
  border-radius: 8px;
}

.placeholder .pi {
  font-size: 20px;
}

.placeholder p {
  margin: 4px 0 0;
  max-width: 240px;
}
</style>
