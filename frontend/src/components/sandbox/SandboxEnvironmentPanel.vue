<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { useModeStore } from '@/stores/mode'
import { useSandboxEnvironmentStore } from '@/stores/sandboxEnvironment'

/**
 * Starts and stops the local LDES server and LDIO of the sandbox, and shows how they work
 * together: the ingest pipeline of the LDIO feeds the event stream of the LDES server.
 */
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })

const environment = useSandboxEnvironmentStore()
const mode = useModeStore()
const confirm = useConfirm()
const toast = useToast()

const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined

onMounted(() => environment.ensureLoaded())
onBeforeUnmount(() => clearInterval(timer))

watch(() => environment.starting, (starting) => {
  clearInterval(timer)
  if (starting) timer = setInterval(() => (now.value = Date.now()), 1000)
}, { immediate: true })

const elapsed = computed(() => environment.startedAt
  ? Math.max(0, Math.round((now.value - environment.startedAt) / 1000)) : 0)

const sandbox = computed(() => environment.sandbox)

const status = computed(() => {
  if (environment.starting) return { label: 'Starting…', severity: 'info', icon: 'pi pi-spin pi-spinner' }
  if (environment.stopping) return { label: 'Stopping…', severity: 'secondary', icon: 'pi pi-spin pi-spinner' }
  switch (environment.state) {
    case 'RUNNING':
      return { label: 'Running', severity: 'success', icon: 'pi pi-check-circle' }
    case 'PARTIAL':
      return { label: 'Partly running', severity: 'warn', icon: 'pi pi-exclamation-triangle' }
    default:
      return { label: 'Stopped', severity: 'secondary', icon: 'pi pi-stop-circle' }
  }
})

async function start() {
  const started = await environment.start()
  if (started) {
    toast.add({
      severity: 'success',
      summary: 'Sandbox running',
      detail: `Members sent to ${sandbox.value?.ingestPipeline} end up in ${sandbox.value?.eventStream}.`,
      life: 5000,
    })
  }
}

function stop() {
  confirm.require({
    header: 'Stop the sandbox?',
    message: 'The sandbox LDES server and LDIO are stopped and removed, with every member and pipeline in them.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: 'Stop sandbox', severity: 'danger' },
    accept: async () => {
      if (await environment.stop()) {
        toast.add({ severity: 'info', summary: 'Sandbox stopped', life: 3500 })
      }
    },
  })
}
</script>

<template>
  <div class="environment" :class="{ compact }" data-cy="sandbox-environment">
    <div class="header">
      <div class="title">
        <i class="pi pi-box" />
        <div>
          <div class="card-title">Sandbox environment</div>
          <div v-if="!compact" class="card-subtitle">A local LDES server and LDIO that work together out of the box</div>
        </div>
      </div>
      <Tag :severity="status.severity" :icon="status.icon" :value="status.label" data-cy="sandbox-state" />
    </div>

    <Message v-if="environment.error" severity="error" :closable="false" class="error" data-cy="sandbox-error">
      {{ environment.error }}
    </Message>

    <div v-if="environment.starting" class="starting">
      <p>
        Starting a local LDES server (with Postgres) and an LDIO in Docker, and preparing them…
        <span class="numeric muted">{{ elapsed }} s</span>
      </p>
      <p class="muted small">The first start downloads the Docker images, which can take a few minutes.</p>
    </div>

    <template v-else-if="sandbox && sandbox.state !== 'STOPPED'">
      <div class="flow">
        <div class="node" :class="{ missing: !sandbox.ldio }">
          <span class="node-kind"><i class="pi pi-share-alt" /> LDIO</span>
          <RouterLink v-if="sandbox.ldio" :to="`/ldios/${sandbox.ldio.id}`" class="node-name">
            {{ sandbox.ldio.name }}
          </RouterLink>
          <span v-else class="node-name muted">Not running</span>
          <span v-if="sandbox.ldio" class="mono small muted">{{ sandbox.ldio.url }}</span>
          <span v-if="sandbox.ldio" class="chip pipeline"><i class="pi pi-sitemap" />{{ sandbox.ingestPipeline }}</span>
        </div>
        <div class="arrow" aria-hidden="true">
          <span class="arrow-label">Ldio:HttpOut</span>
          <i class="pi pi-arrow-right" />
        </div>
        <div class="node" :class="{ missing: !sandbox.ldesServer }">
          <span class="node-kind"><i class="pi pi-server" /> LDES server</span>
          <RouterLink v-if="sandbox.ldesServer" :to="`/ldes-servers/${sandbox.ldesServer.id}`" class="node-name">
            {{ sandbox.ldesServer.name }}
          </RouterLink>
          <span v-else class="node-name muted">Not running</span>
          <span v-if="sandbox.ldesServer" class="mono small muted">{{ sandbox.ldesServer.url }}</span>
          <RouterLink v-if="sandbox.ldesServer"
            :to="`/ldes-servers/${sandbox.ldesServer.id}/eventstreams/${sandbox.eventStream}`" class="chip">
            <i class="pi pi-database" />{{ sandbox.eventStream }}
          </RouterLink>
        </div>
      </div>
      <p v-if="sandbox.eventStreamUrl && !compact" class="muted small hint">
        Inside the sandbox, pipelines reach the event stream on <code>{{ sandbox.eventStreamUrl }}</code>.
      </p>
      <p v-if="sandbox.state === 'PARTIAL'" class="muted small hint">
        Only part of the sandbox runs. Start it again to start what is missing.
      </p>
    </template>

    <p v-else-if="!compact" class="muted intro">
      Starts a local LDES server and LDIO in Docker. Out of the box, the LDIO pipeline
      <code>sandbox-ingest</code> receives members over HTTP and ingests them in the event stream
      <code>sandbox-parcels</code>, which has a paginated and a time-based view.
      <template v-if="mode.production"> Docker has to run on the machine of the LDES-admin backend.</template>
    </p>
    <p v-else class="muted small intro">Not running.</p>

    <div class="actions">
      <Button v-if="environment.state !== 'RUNNING' || environment.starting" label="Start sandbox"
        icon="pi pi-play" size="small" :loading="environment.starting" :disabled="environment.busy"
        data-cy="start-sandbox" @click="start" />
      <Button v-if="environment.state !== 'STOPPED'" label="Stop" icon="pi pi-stop" severity="danger"
        outlined size="small" :loading="environment.stopping" :disabled="environment.busy"
        data-cy="stop-sandbox" @click="stop" />
    </div>
  </div>
</template>

<style scoped>
.environment {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.environment.compact {
  width: 440px;
  gap: 12px;
}

.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.title {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.title > .pi {
  margin-top: 3px;
  color: var(--p-primary-color);
}

.error {
  margin: 0;
}

.starting p {
  margin: 0 0 4px;
}

.flow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.node {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--card-border);
  background: var(--p-content-hover-background);
  min-width: 0;
}

.node.missing {
  border-style: dashed;
  background: none;
}

.node-kind {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--muted);
}

.node-kind .pi {
  font-size: 10px;
}

.node-name {
  font-weight: 600;
}

.node .mono {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node .chip {
  margin-top: 4px;
  max-width: 100%;
}

.arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: var(--muted);
}

.arrow-label {
  font-size: 10.5px;
  font-family: var(--font-mono);
}

.hint,
.intro {
  margin: 0;
  line-height: 1.5;
}

.actions {
  display: flex;
  gap: 8px;
}
</style>
