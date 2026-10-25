<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Listbox from 'primevue/listbox'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import Select from 'primevue/select'
import Skeleton from 'primevue/skeleton'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Badge from 'primevue/badge'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import PageHeader from '@/components/common/PageHeader.vue'
import HealthBadge from '@/components/common/HealthBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import BackgroundProcesses from '@/components/ldes/BackgroundProcesses.vue'
import CreateEventStreamDialog from '@/components/ldes/CreateEventStreamDialog.vue'
import IngestPanel from '@/components/ldes/IngestPanel.vue'
import RdfDocumentEditor from '@/components/ldes/RdfDocumentEditor.vue'
import CodeEditor from '@/components/common/CodeEditor.vue'
import { api } from '@/api'
import { useAction, useAsync, usePolling } from '@/composables/useAsync'
import { type EventStream, paginatedShare, type StreamStatistics } from '@/domain/ldes'
import { useInstancesStore } from '@/stores/instances'
import { useUiStore } from '@/stores/ui'
import { formatCompact, formatNumber, formatPercent, shortIri } from '@/utils/format'

const props = defineProps<{ id: string }>()

const route = useRoute()
const router = useRouter()
const confirm = useConfirm()
const toast = useToast()
const instances = useInstancesStore()
const ui = useUiStore()
const { run } = useAction()

const { data: server, error: serverError } = useAsync(() => api.getInstance('LDES_SERVER', props.id),
  { watch: [() => props.id] })
const { data: health, reload: reloadHealth } = useAsync(() => api.checkHealth('LDES_SERVER', props.id),
  { watch: [() => props.id] })
const streams = useAsync(() => api.listEventStreams(props.id), { watch: [() => props.id] })
const statistics = useAsync(() => api.getStreamStatistics(props.id), { watch: [() => props.id] })
const processes = useAsync(() => api.getBackgroundProcesses(props.id), { watch: [() => props.id] })
const catalog = useAsync(() => api.getCatalog(props.id), { watch: [() => props.id] })
const serverCatalog = useAsync(() => api.getServerCatalog(props.id), { watch: [() => props.id] })

const tab = ref(String(route.query.tab ?? 'streams'))
watch(tab, (value) => router.replace({ query: { ...route.query, tab: value } }))

const showCreate = ref(false)
const shapeCollection = ref<string>()
const shape = useAsync(async () => shapeCollection.value
  ? api.getShape(props.id, shapeCollection.value) : undefined, { watch: [shapeCollection] })

watch(() => streams.data.value, (list) => {
  if (list?.length && !list.some((s) => s.collection === shapeCollection.value)) {
    shapeCollection.value = list[0].collection
  }
})

const up = computed(() => health.value?.status === 'UP')

// The runs of the background processes change by the second: they refresh while they show
usePolling(processes.reload, () => ui.processRefreshSeconds, () => tab.value === 'processes' && up.value)
const processesUpdatedAt = ref<Date>()
watch(() => processes.data.value, () => (processesUpdatedAt.value = new Date()))
const refreshOptions = computed(() => {
  const options = [1, 2, 5, 10, 30].map((seconds) =>
    ({ value: seconds, label: seconds === 1 ? 'Every second' : `Every ${seconds} seconds` }))
  const current = ui.processRefreshSeconds
  if (current > 0 && !options.some((option) => option.value === current)) {
    options.push({ value: current, label: `Every ${current} seconds` })
  }
  return [...options.sort((a, b) => a.value - b.value), { value: 0, label: 'Never' }]
})
const runningProcesses = computed(() =>
  (processes.data.value ?? []).filter((p) => p.running).length)
const failedProcesses = computed(() =>
  (processes.data.value ?? []).filter((p) => p.failedRuns > 0).length)

interface StreamRow extends EventStream {
  stats?: StreamStatistics
  paginated: number
}

const rows = computed<StreamRow[]>(() => (streams.data.value ?? []).map((stream) => {
  const stats = statistics.data.value?.find((s) => s.collection === stream.collection)
  return { ...stream, stats, paginated: paginatedShare(stats) }
}))

function openStream(collection: string) {
  router.push(`/ldes-servers/${props.id}/eventstreams/${collection}`)
}

function refresh() {
  reloadHealth()
  streams.reload()
  statistics.reload()
  processes.reload()
}

function deleteStream(stream: EventStream) {
  confirm.require({
    header: `Delete ${stream.collection}?`,
    message: 'The event stream, its views and all its members are deleted from the server. This cannot be undone.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: 'Delete event stream', severity: 'danger' },
    accept: async () => {
      await run(() => api.deleteEventStream(props.id, stream.collection), `${stream.collection} deleted`)
      streams.reload()
      statistics.reload()
    },
  })
}

function removeServer() {
  const instance = server.value
  if (!instance) return
  const local = instance.origin === 'LOCAL'
  confirm.require({
    header: local ? `Stop ${instance.name}?` : `Remove ${instance.name}?`,
    message: local
      ? 'The local LDES server and its Postgres container are stopped and removed.'
      : 'LDES-admin forgets this LDES server. The server itself keeps running.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: local ? 'Stop and remove' : 'Remove', severity: 'danger' },
    accept: async () => {
      await run(() => instances.remove(instance), `${instance.name} removed`)
      router.push('/ldes-servers')
    },
  })
}

function onCreated(stream: EventStream) {
  toast.add({ severity: 'success', summary: `Event stream ${stream.collection} created`, life: 3500 })
  streams.reload()
  statistics.reload()
}

function saveShape(document: string) {
  return api.updateShape(props.id, shapeCollection.value!, document)
}

async function saveCatalog(document: string) {
  const saved = await api.saveServerCatalog(props.id, document)
  serverCatalog.data.value = saved
  catalog.reload()
  return saved
}
</script>

<template>
  <div class="page">
    <Message v-if="serverError" severity="error" :closable="false">{{ serverError }}</Message>
    <template v-else-if="server">
      <PageHeader :title="server.name" icon="pi pi-server">
        <template #tags>
          <HealthBadge :status="health?.status" />
          <span class="chip">
            <i :class="server.origin === 'LOCAL' ? 'pi pi-desktop' : 'pi pi-link'" />
            {{ server.origin === 'LOCAL' ? 'Local container' : 'Registered' }}
          </span>
          <span v-if="server.sandbox" class="chip"><i class="pi pi-box" />Sandbox</span>
        </template>
        <template #subtitle>
          <a :href="server.url" target="_blank" rel="noopener">{{ server.url }}</a>
          <span v-if="server.internalUrl" v-tooltip.bottom="'Where other local instances, like a local LDIO, reach this server. It publishes its IRIs on this URL.'"
            class="internal-url" data-cy="internal-url">
            <i class="pi pi-sitemap" /> {{ server.internalUrl }}
          </span>
        </template>
        <template #actions>
          <Button icon="pi pi-refresh" severity="secondary" text rounded aria-label="Refresh"
            v-tooltip.bottom="'Refresh'" @click="refresh" />
          <Button :label="server.origin === 'LOCAL' ? 'Stop' : 'Remove'"
            :icon="server.origin === 'LOCAL' ? 'pi pi-stop' : 'pi pi-trash'" severity="danger" outlined
            @click="removeServer" />
          <Button label="New event stream" icon="pi pi-plus" :disabled="!up" data-cy="new-stream"
            @click="showCreate = true" />
        </template>
      </PageHeader>

      <div v-if="health && !up" class="card">
        <EmptyState icon="pi pi-times-circle" title="This LDES server does not respond"
          :description="`${server.url} could not be reached. Check that it runs and that its admin API is exposed.`">
          <Button label="Check again" icon="pi pi-refresh" @click="refresh" />
        </EmptyState>
      </div>

      <Tabs v-else v-model:value="tab">
        <TabList>
          <Tab value="streams" data-cy="tab-streams">
            <i class="pi pi-database" /> Event streams
            <Badge v-if="streams.data.value" :value="streams.data.value.length" severity="secondary" />
          </Tab>
          <Tab value="ingest" data-cy="tab-ingest"><i class="pi pi-upload" /> Ingest</Tab>
          <Tab value="processes" data-cy="tab-processes">
            <i class="pi pi-cog" /> Background processes
            <Badge v-if="failedProcesses" :value="failedProcesses" severity="danger" />
            <Badge v-else-if="runningProcesses" :value="runningProcesses" severity="info" />
          </Tab>
          <Tab value="shapes" data-cy="tab-shapes"><i class="pi pi-check-square" /> Shapes</Tab>
          <Tab value="dcat" data-cy="tab-dcat"><i class="pi pi-book" /> DCAT</Tab>
        </TabList>
        <TabPanels>
          <TabPanel value="streams">
            <div class="card flush">
              <DataTable :value="rows" :loading="streams.loading.value && !streams.data.value"
                row-hover class="streams" data-cy="streams-table"
                :row-class="() => 'clickable-row'" @row-click="openStream($event.data.collection)">
                <template #empty>
                  <EmptyState icon="pi pi-database" title="No event streams yet"
                    description="Create an event stream to start ingesting members.">
                    <Button label="New event stream" icon="pi pi-plus" @click="showCreate = true" />
                  </EmptyState>
                </template>
                <Column header="Collection">
                  <template #body="{ data }">
                    <div class="collection">
                      <span class="collection-name">{{ data.collection }}</span>
                      <span class="muted small">
                        {{ shortIri(data.timestampPath) }}
                        <template v-if="data.createVersions"> · creates versions</template>
                      </span>
                    </div>
                  </template>
                </Column>
                <Column header="Views">
                  <template #body="{ data }">
                    <div class="row-wrap">
                      <span v-for="view in data.views" :key="view" class="chip">{{ view }}</span>
                      <span v-if="!data.views.length" class="muted small">None</span>
                    </div>
                  </template>
                </Column>
                <Column header="Members" class="numeric" header-class="right" body-class="right">
                  <template #body="{ data }">
                    <span v-tooltip.top="formatNumber(data.stats?.ingestedMembers)">
                      {{ formatCompact(data.stats?.ingestedMembers) }}
                    </span>
                  </template>
                </Column>
                <Column header="Paginated" style="width: 220px">
                  <template #body="{ data }">
                    <div class="pagination">
                      <ProgressBar :value="Math.floor(data.paginated * 100)" :show-value="false"
                        class="bar" :class="{ behind: data.paginated < 0.99 }" />
                      <span class="small numeric">{{ formatPercent(data.paginated) }}</span>
                    </div>
                  </template>
                </Column>
                <Column header="Backlog" header-class="right" body-class="right">
                  <template #body="{ data }">
                    <span :class="{ warn: (data.stats?.backlog ?? 0) > 0 }" class="numeric">
                      <i v-if="(data.stats?.backlog ?? 0) > 0" class="pi pi-hourglass" />
                      {{ formatCompact(data.stats?.backlog ?? 0) }}
                    </span>
                  </template>
                </Column>
                <Column style="width: 96px" body-class="right">
                  <template #body="{ data }">
                    <Button icon="pi pi-trash" severity="danger" text rounded size="small"
                      aria-label="Delete event stream" v-tooltip.left="'Delete'"
                      @click.stop="deleteStream(data)" />
                    <Button icon="pi pi-angle-right" text rounded size="small" aria-label="Open"
                      @click.stop="openStream(data.collection)" />
                  </template>
                </Column>
              </DataTable>
            </div>
          </TabPanel>

          <TabPanel value="ingest">
            <IngestPanel v-if="streams.data.value?.length" :server-id="id" :streams="streams.data.value"
              @ingested="statistics.reload()" />
            <div v-else class="card">
              <EmptyState icon="pi pi-upload" title="Nothing to ingest in"
                description="Create an event stream first." />
            </div>
          </TabPanel>

          <TabPanel value="processes">
            <div class="processes-bar">
              <span v-if="processesUpdatedAt" class="muted small" data-cy="processes-updated">
                Updated at {{ processesUpdatedAt.toLocaleTimeString('en-GB') }}
              </span>
              <span class="spacer" />
              <label class="muted small" for="process-refresh">Refresh</label>
              <Select v-model="ui.processRefreshSeconds" input-id="process-refresh" :options="refreshOptions"
                option-label="label" option-value="value" size="small" data-cy="process-refresh" />
            </div>
            <BackgroundProcesses v-if="processes.data.value" :processes="processes.data.value" />
            <Skeleton v-else height="200px" border-radius="12px" />
            <p class="muted small note">
              <i class="pi pi-info-circle" /> From the batch metrics of the server, since it started. The jobs run on
              <code>ldes-server.fragmentation-cron</code> and <code>ldes-server.maintenance-cron</code>.
            </p>
          </TabPanel>

          <TabPanel value="shapes">
            <div class="shapes">
              <div class="card flush">
                <Listbox v-model="shapeCollection" :options="streams.data.value ?? []" option-label="collection"
                  option-value="collection" class="stream-list" aria-label="Event streams" />
              </div>
              <RdfDocumentEditor v-if="shapeCollection" :document="shape.data.value"
                :title="`SHACL shape of ${shapeCollection}`"
                description="Members that do not conform to the shape are refused on ingest"
                :save="saveShape"
                :saved-message="`Shape of ${shapeCollection} saved`" />
            </div>
          </TabPanel>

          <TabPanel value="dcat">
            <div class="dcat">
              <RdfDocumentEditor :document="serverCatalog.data.value?.document" title="Server catalog"
                data-cy="server-catalog"
                :description="serverCatalog.data.value?.catalogId
                  ? 'The dcat:Catalog describing this server. Keep it a blank node: the server gives it its IRI.'
                  : 'This server has no catalog yet. Describe it, and save to publish it.'"
                :save="saveCatalog"
                saved-message="Server catalog saved" />
              <div class="card">
                <div class="card-header">
                  <div>
                    <div class="card-title">Published catalog</div>
                    <div class="card-subtitle">What the server serves: its catalog, with the datasets and data services of the event streams and views that have DCAT</div>
                  </div>
                </div>
                <CodeEditor v-if="catalog.data.value" :model-value="catalog.data.value" language="turtle" readonly
                  min-height="320px" max-height="560px" data-cy="published-catalog" />
                <EmptyState v-else-if="catalog.data.value !== undefined" icon="pi pi-book"
                  title="Nothing published yet" description="The server publishes DCAT once it has a catalog." />
                <Skeleton v-else height="320px" />
              </div>
            </div>
          </TabPanel>
        </TabPanels>
      </Tabs>

      <CreateEventStreamDialog v-model:visible="showCreate" :server-id="id" :server-url="server.url"
        @created="onCreated" />
    </template>
    <Skeleton v-else height="420px" border-radius="12px" />
  </div>
</template>

<style scoped>
:deep(.p-tab) {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collection {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.collection-name {
  font-weight: 600;
  font-family: var(--font-mono);
  font-size: 13px;
}

:deep(.right) {
  text-align: right !important;
}

:deep(th.right .p-datatable-column-header-content) {
  justify-content: flex-end;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bar {
  flex: 1;
  height: 6px;
}

.bar.behind :deep(.p-progressbar-value) {
  background: var(--p-amber-500);
}

.warn {
  color: var(--p-amber-600);
  font-weight: 600;
  white-space: nowrap;
}

.warn .pi {
  font-size: 11px;
}

.note {
  margin-top: 16px;
}

.processes-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.internal-url {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 14px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.internal-url .pi {
  font-size: 11px;
}

.dcat {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 20px;
  align-items: start;
}

.shapes {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.stream-list {
  border: none;
  width: 100%;
}
</style>
