<script setup lang="ts">
import { computed, markRaw, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type Connection, MarkerType, type NodeMouseEvent, useVueFlow, VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import Button from 'primevue/button'
import Menu from 'primevue/menu'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import ComponentInspector from '@/components/sandbox/ComponentInspector.vue'
import ComponentPalette from '@/components/sandbox/ComponentPalette.vue'
import DeployDialog from '@/components/sandbox/DeployDialog.vue'
import PipelineNode from '@/components/sandbox/PipelineNode.vue'
import PipelineYamlDialog from '@/components/sandbox/PipelineYamlDialog.vue'
import SimulationPanel from '@/components/sandbox/SimulationPanel.vue'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import type { Pipeline } from '@/domain/ldio'
import { kinds } from '@/pipeline/catalog'
import { connectionProblem, type GraphNode } from '@/pipeline/graph'
import { defaultTemplate, templates } from '@/pipeline/templates'
import { useInstancesStore } from '@/stores/instances'
import { useSandboxStore } from '@/stores/sandbox'
import { useUiStore } from '@/stores/ui'

const FLOW_ID = 'sandbox'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const sandbox = useSandboxStore()
const instances = useInstancesStore()
const ui = useUiStore()
const { screenToFlowCoordinate, fitView, onNodesInitialized } = useVueFlow(FLOW_ID)

const nodeTypes = { ldio: markRaw(PipelineNode) }
provide('pipelineIssues', sandbox.issuesOf)
provide('selectedNodeId', computed(() => sandbox.selectedId))

const templateMenu = ref<InstanceType<typeof Menu>>()
const showYaml = ref(false)
const showDeploy = ref(false)
const simulationOpen = ref(false)
let fitPending = true
// The canvas needs the room: the navigation collapses while in the sandbox
let sidebarWasCollapsed = false

// The canvas changes size with the simulation panel: keep the pipeline in view
watch(simulationOpen, () => setTimeout(() => fitView({ padding: 0.2, maxZoom: 1.1, duration: 250 }), 220))

function run() {
  simulationOpen.value = true
  sandbox.run()
}

const templateItems = computed(() => [
  {
    label: 'Start from a template',
    items: templates.map((template) => ({
      label: template.title,
      icon: template.icon,
      command: () => replace(() => sandbox.loadTemplate(template.id)),
    })),
  },
  {
    label: 'Or',
    items: [{ label: 'Empty canvas', icon: 'pi pi-file', command: () => replace(() => sandbox.clear()) }],
  },
])

const originLabel = computed(() => {
  const origin = sandbox.origin
  if (origin.kind === 'ldio') return `Deployed on ${origin.ldioName}`
  if (origin.kind === 'template') return 'From a template'
  return 'New pipeline'
})

onMounted(async () => {
  sidebarWasCollapsed = ui.sidebarCollapsed
  ui.sidebarCollapsed = true
  await instances.ensureLoaded()
  const { ldio, pipeline, template } = route.query
  if (typeof ldio === 'string' && typeof pipeline === 'string') {
    await openDeployed(ldio, pipeline)
  } else if (typeof template === 'string') {
    sandbox.loadTemplate(template)
  } else if (route.query.new !== undefined) {
    sandbox.clear()
  } else {
    sandbox.ensureInitialised()
  }
  if (Object.keys(route.query).length) router.replace({ query: {} })
})

onBeforeUnmount(() => {
  ui.sidebarCollapsed = sidebarWasCollapsed
})

onNodesInitialized(() => {
  if (fitPending) {
    fitPending = false
    fitView({ padding: 0.25, maxZoom: 1.1 })
  }
})

async function openDeployed(ldioId: string, name: string) {
  try {
    const deployed = (await api.listPipelines(ldioId)).find((p) => p.pipeline.name === name)
    if (!deployed) throw new Error(`No pipeline ${name} on this LDIO`)
    sandbox.load(deployed.pipeline, sampleFor(deployed.pipeline),
      { kind: 'ldio', ldioId, ldioName: instances.find(ldioId)?.name ?? 'LDIO' })
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Could not open the pipeline', detail: errorMessage(e), life: 6000 })
    sandbox.ensureInitialised()
  }
}

/** The template sample that fits the input of a pipeline best */
function sampleFor(pipeline: Pipeline) {
  const match = templates.find((t) => t.pipeline.input.adapter?.name === pipeline.input.adapter?.name
    && t.pipeline.input.name === pipeline.input.name)
    ?? templates.find((t) => t.pipeline.input.adapter?.name === pipeline.input.adapter?.name)
  return (match ?? defaultTemplate).sample
}

function replace(load: () => void) {
  const apply = () => {
    fitPending = true
    load()
  }
  if (sandbox.nodes.length === 0) {
    apply()
    return
  }
  confirm.require({
    header: 'Replace the pipeline?',
    message: 'The pipeline on the canvas is replaced. Deploy or copy its YAML first if you want to keep it.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: 'Replace' },
    accept: apply,
  })
}

function find(id: string): GraphNode | undefined {
  return sandbox.nodes.find((node) => node.id === id)
}

/**
 * Vue Flow also validates existing edges with this when they are (re)set, so an edge must not be
 * compared with itself.
 */
function isValidConnection(connection: Connection) {
  const others = sandbox.edges.filter((edge) =>
    edge.source !== connection.source || edge.target !== connection.target)
  return !connectionProblem(find(connection.source), find(connection.target), others)
}

function onConnect(connection: Connection) {
  const problem = sandbox.connect(connection.source, connection.target)
  if (problem) toast.add({ severity: 'warn', summary: problem, life: 3500 })
}

function onNodeClick({ node }: NodeMouseEvent) {
  sandbox.selectedId = node.id
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
}

function onDrop(event: DragEvent) {
  const component = event.dataTransfer?.getData('application/ldio-component')
  if (!component) return
  const position = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  sandbox.addComponent(component, { x: position.x - 115, y: position.y - 35 })
}

function layout() {
  sandbox.layout()
  setTimeout(() => fitView({ padding: 0.25, maxZoom: 1.1, duration: 300 }), 30)
}

function minimapColor(node: { data?: GraphNode['data'] }) {
  return node.data ? kinds[node.data.kind].color : '#94a3b8'
}
</script>

<template>
  <div class="sandbox">
    <aside class="side palette-side">
      <ComponentPalette @add="sandbox.addComponent" />
    </aside>

    <section class="workspace">
      <header class="toolbar">
        <div class="pipeline-title">
          <span class="mono name" :title="originLabel">{{ sandbox.name || 'untitled' }}</span>
          <button type="button" class="validity" :class="{ invalid: sandbox.errors.length }"
            data-cy="pipeline-validity" @click="sandbox.selectedId = undefined">
            <template v-if="sandbox.errors.length">
              <i class="pi pi-times-circle" /> {{ sandbox.errors.length }} {{ sandbox.errors.length === 1 ? 'problem' : 'problems' }}
            </template>
            <template v-else>
              <i class="pi pi-check-circle" /> Valid
              <span v-if="sandbox.warnings.length" class="warnings">· {{ sandbox.warnings.length }} warnings</span>
            </template>
          </button>
        </div>
        <div class="toolbar-actions">
          <Button icon="pi pi-clone" severity="secondary" text size="small" v-tooltip.bottom="'Templates'"
            aria-label="Templates" aria-haspopup="true" data-cy="templates" @click="templateMenu?.toggle($event)" />
          <Menu ref="templateMenu" :model="templateItems" popup />
          <Button icon="pi pi-sitemap" severity="secondary" text size="small" v-tooltip.bottom="'Tidy up the layout'"
            aria-label="Tidy up the layout" @click="layout" />
          <Button icon="pi pi-code" severity="secondary" text size="small" v-tooltip.bottom="'Pipeline YAML'"
            aria-label="Pipeline YAML" data-cy="open-yaml" @click="showYaml = true" />
          <Button label="Run" icon="pi pi-play" severity="secondary" outlined size="small"
            :disabled="!sandbox.conversion.valid" :loading="sandbox.running" @click="run" />
          <Button label="Deploy" icon="pi pi-cloud-upload" size="small" :disabled="!sandbox.conversion.valid"
            data-cy="deploy" @click="showDeploy = true" />
        </div>
      </header>

      <div class="canvas" @dragover="onDragOver" @drop="onDrop">
        <VueFlow
          :id="FLOW_ID"
          v-model:nodes="sandbox.nodes"
          v-model:edges="sandbox.edges"
          :node-types="nodeTypes"
          :default-edge-options="{ markerEnd: { type: MarkerType.ArrowClosed, width: 18, height: 18 } }"
          :is-valid-connection="isValidConnection"
          :delete-key-code="['Backspace', 'Delete']"
          :snap-to-grid="true"
          :snap-grid="[10, 10]"
          :min-zoom="0.3"
          :max-zoom="1.75"
          :connection-radius="30"
          fit-view-on-init
          data-cy="canvas"
          @connect="onConnect"
          @node-click="onNodeClick"
          @pane-click="sandbox.selectedId = undefined"
        >
          <Background :gap="20" :size="1.2" pattern-color="var(--canvas-dots)" />
          <Controls position="bottom-left" />
          <MiniMap position="bottom-right" pannable zoomable :width="150" :height="100" :node-color="minimapColor"
            :node-border-radius="6" mask-color="var(--canvas-mask)" />
        </VueFlow>
        <div v-if="!sandbox.nodes.length" class="canvas-empty">
          <i class="pi pi-arrow-left" />
          <h3>Drag components onto the canvas</h3>
          <p>Start with an input, connect it to an adapter, chain transformers and finish with one or more outputs.</p>
          <Button label="Or start from a template" icon="pi pi-clone" text size="small"
            @click="templateMenu?.toggle($event)" />
        </div>
        <div class="legend">
          <span v-for="(info, kind) in kinds" :key="kind" class="legend-item">
            <span class="kind-dot" :style="{ background: info.color }" />{{ info.label }}
          </span>
        </div>
      </div>
      <SimulationPanel v-model:open="simulationOpen" />
    </section>

    <aside class="side inspector-side">
      <ComponentInspector @edit-yaml="showYaml = true" />
    </aside>

    <PipelineYamlDialog v-model:visible="showYaml" />
    <DeployDialog v-model:visible="showDeploy" />
  </div>
</template>

<style scoped>
.sandbox {
  --canvas-dots: #cbd5e1;
  --canvas-mask: rgba(241, 245, 249, 0.7);
  --canvas-bg: #f8fafc;
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr) 330px;
  /* The panels scroll inside, they never stretch the sandbox */
  grid-template-rows: minmax(0, 1fr);
  height: 100%;
  min-height: 0;
}

.app-dark .sandbox {
  --canvas-dots: #3f3f46;
  --canvas-mask: rgba(9, 9, 11, 0.6);
  --canvas-bg: #0f0f12;
}

.side {
  min-height: 0;
  overflow: hidden;
  background: var(--card-bg);
}

.palette-side {
  border-right: 1px solid var(--card-border);
}

.inspector-side {
  border-left: 1px solid var(--card-border);
}

.workspace {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--card-border);
}

.pipeline-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.name {
  font-weight: 600;
  font-size: 13.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.validity {
  flex: none;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  border: none;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  color: var(--p-green-700);
  background: var(--p-green-50);
}

.validity.invalid {
  color: var(--p-red-700);
  background: var(--p-red-50);
}

.app-dark .validity {
  color: var(--p-green-300);
  background: color-mix(in srgb, var(--p-green-500) 14%, transparent);
}

.app-dark .validity.invalid {
  color: var(--p-red-300);
  background: color-mix(in srgb, var(--p-red-500) 14%, transparent);
}

.warnings {
  font-weight: 500;
  opacity: 0.8;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  flex: none;
  gap: 4px;
}

.canvas {
  position: relative;
  flex: 1;
  min-height: 0;
  background: var(--canvas-bg);
}

.canvas-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  pointer-events: none;
  color: var(--muted);
}

.canvas-empty > * {
  pointer-events: auto;
}

.canvas-empty .pi-arrow-left {
  font-size: 22px;
  margin-bottom: 10px;
}

.canvas-empty h3 {
  color: var(--text);
  font-size: 15px;
}

.canvas-empty p {
  max-width: 380px;
  font-size: 13px;
  line-height: 1.5;
}

.legend {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  gap: 12px;
  padding: 6px 10px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--card-bg) 85%, transparent);
  border: 1px solid var(--card-border);
  font-size: 11.5px;
  color: var(--muted);
  pointer-events: none;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.canvas :deep(.vue-flow__edge-path) {
  stroke: var(--p-surface-400);
  stroke-width: 2;
}

.canvas :deep(.vue-flow__edge.selected .vue-flow__edge-path),
.canvas :deep(.vue-flow__edge:hover .vue-flow__edge-path) {
  stroke: var(--p-primary-500);
}

.canvas :deep(.vue-flow__edge.flowing .vue-flow__edge-path) {
  stroke: var(--p-primary-500);
  stroke-width: 2.5;
}

.canvas :deep(.vue-flow__connection-path) {
  stroke: var(--p-primary-500);
  stroke-width: 2;
}

.canvas :deep(.vue-flow__handle.connectingfrom),
.canvas :deep(.vue-flow__handle.valid) {
  background: var(--p-primary-500);
}

.canvas :deep(.vue-flow__controls) {
  box-shadow: var(--card-shadow);
  border-radius: 8px;
  overflow: hidden;
}

.canvas :deep(.vue-flow__controls-button) {
  background: var(--card-bg);
  border-bottom-color: var(--card-border);
  color: var(--text);
  fill: currentColor;
}

.canvas :deep(.vue-flow__minimap) {
  background: var(--card-bg);
  border: 1px solid var(--card-border);
  border-radius: 8px;
  overflow: hidden;
}
</style>
