<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import Skeleton from 'primevue/skeleton'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Dialog from 'primevue/dialog'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import PageHeader from '@/components/common/PageHeader.vue'
import StatTile from '@/components/common/StatTile.vue'
import CodeEditor from '@/components/common/CodeEditor.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import FragmentBrowser from '@/components/ldes/FragmentBrowser.vue'
import CreateViewDialog from '@/components/ldes/CreateViewDialog.vue'
import IngestPanel from '@/components/ldes/IngestPanel.vue'
import RdfDocumentEditor from '@/components/ldes/RdfDocumentEditor.vue'
import { api } from '@/api'
import { useAction, useAsync } from '@/composables/useAsync'
import { paginatedShare, type TypedConfig, type View } from '@/domain/ldes'
import { formatCompact, formatNumber, formatPercent, shortIri } from '@/utils/format'

const props = defineProps<{ id: string; collection: string }>()

const route = useRoute()
const router = useRouter()
const confirm = useConfirm()
const toast = useToast()
const { run } = useAction()

const deps = { watch: [() => props.id, () => props.collection] }
const { data: server } = useAsync(() => api.getInstance('LDES_SERVER', props.id), deps)
const { data: stream, error } = useAsync(() => api.getEventStream(props.id, props.collection), deps)
const views = useAsync(() => api.listViews(props.id, props.collection), deps)
const statistics = useAsync(async () => (await api.getStreamStatistics(props.id))
  .find((s) => s.collection === props.collection), deps)
const shape = useAsync(() => api.getShape(props.id, props.collection), deps)

const tab = ref(String(route.query.tab ?? 'views'))
watch(tab, (value) => router.replace({ query: { ...route.query, tab: value } }))
const browsedView = ref<string>()
const showCreateView = ref(false)
const shownDefinition = ref<View>()

const stats = computed(() => statistics.data.value)

function viewStatistics(view: string) {
  return stats.value?.views.find((v) => v.view === view)
}

function describe(config: TypedConfig): string {
  const name = config.type.replace(/Fragmentation$/, '').replace(/([a-z])([A-Z])/g, '$1 $2')
  const details = Object.entries(config.properties)
    .map(([key, value]) => key === 'fragmentationPath' ? `on ${shortIri(value)}` : `${key} ${value}`)
  return details.length ? `${name} · ${details.join(', ')}` : name
}

function retention(config: TypedConfig): string {
  switch (config.type) {
    case 'DurationAgoPolicy':
      return `Keeps members of the last ${config.properties.value}`
    case 'LatestVersionSubset':
      return `Keeps the latest ${config.properties.amount} version(s)`
    case 'PointInTimePolicy':
      return `Keeps members after ${config.properties.pointInTime}`
    default:
      return config.type
  }
}

function browse(view: string) {
  browsedView.value = view
  tab.value = 'browse'
}

function deleteView(view: View) {
  confirm.require({
    header: `Delete view ${view.name}?`,
    message: 'Its pages are removed from the server. The members stay in the event stream.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: 'Delete view', severity: 'danger' },
    accept: async () => {
      await run(() => api.deleteView(props.id, props.collection, view.name), `View ${view.name} deleted`)
      views.reload()
      statistics.reload()
    },
  })
}

function deleteStream() {
  confirm.require({
    header: `Delete ${props.collection}?`,
    message: 'The event stream, its views and all its members are deleted from the server. This cannot be undone.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: 'Delete event stream', severity: 'danger' },
    accept: async () => {
      const deleted = await run(async () => {
        await api.deleteEventStream(props.id, props.collection)
        return true
      }, `${props.collection} deleted`)
      if (deleted) router.push(`/ldes-servers/${props.id}`)
    },
  })
}

function onViewCreated(view: View) {
  toast.add({ severity: 'success', summary: `View ${view.name} created`, life: 3500 })
  views.reload()
  statistics.reload()
}

function saveShape(document: string) {
  return api.updateShape(props.id, props.collection, document)
}
</script>

<template>
  <div class="page">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <template v-else-if="stream && server">
      <PageHeader :title="stream.collection" icon="pi pi-database">
        <template #tags>
          <span class="chip"><i class="pi pi-clock" />{{ shortIri(stream.timestampPath) }}</span>
          <span class="chip"><i class="pi pi-history" />{{ shortIri(stream.versionOfPath) }}</span>
          <span v-if="stream.createVersions" class="chip"><i class="pi pi-copy" />Creates versions</span>
        </template>
        <template #subtitle>
          Event stream on <RouterLink :to="`/ldes-servers/${id}`">{{ server.name }}</RouterLink> ·
          <a :href="`${server.url}/${stream.collection}`" target="_blank" rel="noopener" class="mono">
            {{ server.url }}/{{ stream.collection }}</a>
        </template>
        <template #actions>
          <Button label="Delete" icon="pi pi-trash" severity="danger" outlined @click="deleteStream" />
          <Button label="Ingest member" icon="pi pi-upload" severity="secondary" outlined @click="tab = 'ingest'" />
          <Button label="New view" icon="pi pi-plus" data-cy="new-view" @click="showCreateView = true" />
        </template>
      </PageHeader>

      <section class="tiles">
        <StatTile label="Members ingested" :value="formatCompact(stats?.ingestedMembers)" icon="pi pi-download"
          :caption="formatNumber(stats?.ingestedMembers)" />
        <StatTile label="Views" :value="String(stream.views.length)" icon="pi pi-th-large"
          :caption="stream.views.join(', ') || 'None yet'" />
        <StatTile label="Waiting to be paginated" :value="formatCompact(stats?.backlog ?? 0)" icon="pi pi-hourglass"
          :tone="(stats?.backlog ?? 0) > 0 ? 'warning' : 'good'" caption="In the slowest view" />
        <StatTile label="Paginated" :value="formatPercent(paginatedShare(stats))" icon="pi pi-book"
          caption="Of the bucketised members, slowest view" />
      </section>

      <Tabs v-model:value="tab">
        <TabList>
          <Tab value="views" data-cy="tab-views"><i class="pi pi-th-large" /> Views</Tab>
          <Tab value="browse" data-cy="tab-browse"><i class="pi pi-compass" /> Browse</Tab>
          <Tab value="definition"><i class="pi pi-code" /> Definition</Tab>
          <Tab value="shape"><i class="pi pi-check-square" /> Shape</Tab>
          <Tab value="ingest"><i class="pi pi-upload" /> Ingest</Tab>
        </TabList>
        <TabPanels>
          <TabPanel value="views">
            <div v-if="views.data.value?.length" class="card-grid">
              <article v-for="view in views.data.value" :key="view.name" class="card view" data-cy="view-card">
                <header class="view-header">
                  <div>
                    <h3 class="mono">{{ view.name }}</h3>
                    <span class="muted small">{{ formatNumber(view.pageSize) }} members per page</span>
                  </div>
                  <Button icon="pi pi-trash" severity="danger" text rounded size="small" aria-label="Delete view"
                    @click="deleteView(view)" />
                </header>
                <div class="view-config">
                  <span v-for="fragmentation in view.fragmentations" :key="fragmentation.type" class="chip">
                    <i class="pi pi-sitemap" />{{ describe(fragmentation) }}
                  </span>
                  <span v-if="!view.fragmentations.length" class="chip"><i class="pi pi-book" />Pagination only</span>
                  <span v-for="policy in view.retentionPolicies" :key="policy.type" class="chip retention">
                    <i class="pi pi-clock" />{{ retention(policy) }}
                  </span>
                </div>
                <div v-if="viewStatistics(view.name)" class="view-progress">
                  <div class="progress-line">
                    <span class="small muted">Bucketised</span>
                    <span class="small numeric">{{ formatNumber(viewStatistics(view.name)!.bucketisedMembers) }}</span>
                  </div>
                  <div class="progress-line">
                    <span class="small muted">Paginated</span>
                    <span class="small numeric">{{ formatNumber(viewStatistics(view.name)!.paginatedMembers) }}</span>
                  </div>
                  <ProgressBar :show-value="false" class="bar"
                    :value="viewStatistics(view.name)!.bucketisedMembers
                      ? Math.floor(viewStatistics(view.name)!.paginatedMembers / viewStatistics(view.name)!.bucketisedMembers * 100) : 100" />
                </div>
                <footer class="view-footer">
                  <Button label="Definition" icon="pi pi-code" text size="small" @click="shownDefinition = view" />
                  <Button label="Browse" icon="pi pi-compass" size="small" severity="secondary"
                    data-cy="browse-view-button" @click="browse(view.name)" />
                </footer>
              </article>
            </div>
            <div v-else-if="views.data.value" class="card">
              <EmptyState icon="pi pi-th-large" title="No views yet"
                description="A view fragments the members into pages that clients can follow.">
                <Button label="New view" icon="pi pi-plus" @click="showCreateView = true" />
              </EmptyState>
            </div>
          </TabPanel>
          <TabPanel value="browse">
            <FragmentBrowser v-if="views.data.value?.length" v-model:view="browsedView" :server-id="id"
              :collection="collection" :views="views.data.value" />
            <div v-else class="card">
              <EmptyState icon="pi pi-compass" title="Nothing to browse" description="Create a view first." />
            </div>
          </TabPanel>
          <TabPanel value="definition">
            <div class="card">
              <div class="card-header">
                <div>
                  <div class="card-title">ldes:EventStream</div>
                  <div class="card-subtitle">As returned by the admin API of the server</div>
                </div>
              </div>
              <CodeEditor :model-value="stream.definition" language="turtle" readonly min-height="240px" />
            </div>
          </TabPanel>
          <TabPanel value="shape">
            <RdfDocumentEditor :document="shape.data.value" :title="`SHACL shape of ${collection}`"
              description="Members that do not conform to the shape are refused on ingest"
              :save="saveShape"
              :saved-message="`Shape of ${collection} saved`" />
          </TabPanel>
          <TabPanel value="ingest">
            <IngestPanel :server-id="id" :streams="[stream]" :collection="collection"
              @ingested="statistics.reload()" />
          </TabPanel>
        </TabPanels>
      </Tabs>

      <CreateViewDialog v-model:visible="showCreateView" :server-id="id" :server-url="server.url" :stream="stream"
        @created="onViewCreated" />
      <Dialog :visible="!!shownDefinition" modal :header="`View ${shownDefinition?.name}`" :style="{ width: '720px' }"
        @update:visible="shownDefinition = undefined">
        <CodeEditor v-if="shownDefinition" :model-value="shownDefinition.definition" language="turtle" readonly />
      </Dialog>
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

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.view {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.view-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.view-header h3 {
  font-size: 15px;
  margin-bottom: 2px;
}

.view-config {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.view-config .chip {
  white-space: normal;
  border-radius: 8px;
}

.retention {
  background: color-mix(in srgb, var(--p-amber-500) 10%, transparent);
  border-color: color-mix(in srgb, var(--p-amber-500) 30%, transparent);
}

.view-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--card-border);
}

.progress-line {
  display: flex;
  justify-content: space-between;
}

.bar {
  height: 6px;
  margin-top: 2px;
}

.view-footer {
  display: flex;
  justify-content: space-between;
  margin-top: auto;
}
</style>
