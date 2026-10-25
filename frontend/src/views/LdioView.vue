<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Badge from 'primevue/badge'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import { useConfirm } from 'primevue/useconfirm'
import PageHeader from '@/components/common/PageHeader.vue'
import HealthBadge from '@/components/common/HealthBadge.vue'
import PipelineStatusTag from '@/components/common/PipelineStatusTag.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import CodeEditor from '@/components/common/CodeEditor.vue'
import PipelineChain from '@/components/ldio/PipelineChain.vue'
import PipelinePreview from '@/components/ldio/PipelinePreview.vue'
import { api } from '@/api'
import { useAction, useAsync } from '@/composables/useAsync'
import { canBeHalted, canBeResumed, type DeployedPipeline } from '@/domain/ldio'
import { componentInfo, kindOrder, kinds } from '@/pipeline/catalog'
import { pipelineToYaml } from '@/pipeline/yaml'
import { useInstancesStore } from '@/stores/instances'
import { formatNumber } from '@/utils/format'

const props = defineProps<{ id: string }>()

const route = useRoute()
const router = useRouter()
const confirm = useConfirm()
const instances = useInstancesStore()
const { run } = useAction()

const deps = { watch: [() => props.id] }
const { data: ldio, error } = useAsync(() => api.getInstance('LDIO', props.id), deps)
const { data: health, reload: reloadHealth } = useAsync(() => api.checkHealth('LDIO', props.id), deps)
const pipelines = useAsync(() => api.listPipelines(props.id), deps)
const statistics = useAsync(() => api.getPipelineStatistics(props.id), deps)
const catalog = useAsync(() => api.getComponentCatalog(props.id), deps)
const clientStatuses = useAsync(() => api.getLdesClientStatuses(props.id), deps)

const tab = ref(String(route.query.tab ?? 'pipelines'))
watch(tab, (value) => router.replace({ query: { ...route.query, tab: value } }))
const expanded = ref<Record<string, boolean>>({})

const up = computed(() => health.value?.status === 'UP')
const rows = computed(() => (pipelines.data.value ?? []).map((deployed) => ({
  ...deployed,
  name: deployed.pipeline.name,
  stats: statistics.data.value?.find((s) => s.pipeline === deployed.pipeline.name),
  clientStatus: clientStatuses.data.value?.[deployed.pipeline.name],
})))

const catalogGroups = computed(() => kindOrder.map((kind) => {
  const installed = catalog.data.value?.[`${kind}s` as 'inputs' | 'adapters' | 'transformers' | 'outputs'] ?? []
  return {
    kind,
    info: kinds[kind],
    components: installed.map((id) => componentInfo(id) ?? {
      id, label: id, description: 'Custom component', icon: kinds[kind].icon, docs: '',
    }),
  }
}))

function refresh() {
  reloadHealth()
  pipelines.reload()
  statistics.reload()
  clientStatuses.reload()
}

async function halt(deployed: DeployedPipeline) {
  await run(() => api.haltPipeline(props.id, deployed.pipeline.name), `${deployed.pipeline.name} halted`)
  pipelines.reload()
}

async function resume(deployed: DeployedPipeline) {
  await run(() => api.resumePipeline(props.id, deployed.pipeline.name), `${deployed.pipeline.name} resumed`)
  pipelines.reload()
}

function edit(deployed: DeployedPipeline) {
  router.push({ path: '/sandbox', query: { ldio: props.id, pipeline: deployed.pipeline.name } })
}

function remove(deployed: DeployedPipeline) {
  confirm.require({
    header: `Delete ${deployed.pipeline.name}?`,
    message: 'LDIO stops the pipeline gracefully and removes it. Persisted state (e.g. of an LDES client) is lost.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: 'Delete pipeline', severity: 'danger' },
    accept: async () => {
      await run(() => api.deletePipeline(props.id, deployed.pipeline.name), `${deployed.pipeline.name} deleted`)
      pipelines.reload()
    },
  })
}

function removeLdio() {
  const instance = ldio.value
  if (!instance) return
  const local = instance.origin === 'LOCAL'
  confirm.require({
    header: local ? `Stop ${instance.name}?` : `Remove ${instance.name}?`,
    message: local
      ? 'The local LDIO container and its pipelines are stopped and removed.'
      : 'LDES-admin forgets this LDIO. The LDIO and its pipelines keep running.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: local ? 'Stop and remove' : 'Remove', severity: 'danger' },
    accept: async () => {
      await run(() => instances.remove(instance), `${instance.name} removed`)
      router.push('/ldios')
    },
  })
}

function toggle(name: string) {
  expanded.value = expanded.value[name] ? {} : { [name]: true }
}
</script>

<template>
  <div class="page">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <template v-else-if="ldio">
      <PageHeader :title="ldio.name" icon="pi pi-share-alt">
        <template #tags>
          <HealthBadge :status="health?.status" />
          <span class="chip">
            <i :class="ldio.origin === 'LOCAL' ? 'pi pi-desktop' : 'pi pi-link'" />
            {{ ldio.origin === 'LOCAL' ? 'Local container' : 'Registered' }}
          </span>
          <span v-if="ldio.sandbox" class="chip"><i class="pi pi-box" />Sandbox</span>
        </template>
        <template #subtitle>
          <a :href="ldio.url" target="_blank" rel="noopener">{{ ldio.url }}</a>
          <span v-if="ldio.internalUrl" v-tooltip.bottom="'Where other local instances reach this LDIO'"
            class="internal-url" data-cy="internal-url">
            <i class="pi pi-sitemap" /> {{ ldio.internalUrl }}
          </span>
        </template>
        <template #actions>
          <Button icon="pi pi-refresh" severity="secondary" text rounded aria-label="Refresh"
            v-tooltip.bottom="'Refresh'" @click="refresh" />
          <Button :label="ldio.origin === 'LOCAL' ? 'Stop' : 'Remove'"
            :icon="ldio.origin === 'LOCAL' ? 'pi pi-stop' : 'pi pi-trash'" severity="danger" outlined
            @click="removeLdio" />
          <Button label="New pipeline" icon="pi pi-plus" :disabled="!up" data-cy="new-pipeline"
            @click="router.push({ path: '/sandbox', query: { new: '' } })" />
        </template>
      </PageHeader>

      <div v-if="health && !up" class="card">
        <EmptyState icon="pi pi-times-circle" title="This LDIO does not respond"
          :description="`${ldio.url} could not be reached. Check that it runs and that its pipeline API is exposed.`">
          <Button label="Check again" icon="pi pi-refresh" @click="refresh" />
        </EmptyState>
      </div>

      <Tabs v-else v-model:value="tab">
        <TabList>
          <Tab value="pipelines" data-cy="tab-pipelines">
            <i class="pi pi-sitemap" /> Pipelines
            <Badge v-if="pipelines.data.value" :value="pipelines.data.value.length" severity="secondary" />
          </Tab>
          <Tab value="catalog" data-cy="tab-catalog"><i class="pi pi-th-large" /> Component catalog</Tab>
        </TabList>
        <TabPanels>
          <TabPanel value="pipelines">
            <div class="card flush">
              <DataTable :value="rows" v-model:expanded-rows="expanded" data-key="name" row-hover
                :loading="pipelines.loading.value && !pipelines.data.value" data-cy="pipelines-table">
                <template #empty>
                  <EmptyState icon="pi pi-sitemap" title="No pipelines yet"
                    description="Design a pipeline in the sandbox and deploy it here.">
                    <Button label="Open the sandbox" icon="pi pi-sitemap" @click="router.push('/sandbox')" />
                  </EmptyState>
                </template>
                <Column style="width: 44px">
                  <template #body="{ data }">
                    <Button :icon="expanded[data.name] ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" text rounded
                      size="small" severity="secondary" :aria-label="`Details of ${data.name}`"
                      @click="toggle(data.name)" />
                  </template>
                </Column>
                <Column header="Pipeline">
                  <template #body="{ data }">
                    <div class="pipeline-name">
                      <span class="mono">{{ data.name }}</span>
                      <span class="muted small">{{ data.pipeline.description }}</span>
                      <PipelineChain :pipeline="data.pipeline" class="chain" />
                    </div>
                  </template>
                </Column>
                <Column header="Status" style="width: 170px">
                  <template #body="{ data }">
                    <div class="status">
                      <PipelineStatusTag :status="data.status" />
                      <span v-if="data.clientStatus" class="muted small">
                        <i class="pi pi-cloud-download" /> {{ data.clientStatus.toLowerCase() }}
                      </span>
                      <span v-else-if="data.updateSource === 'MANUAL'" class="muted small">changed manually</span>
                    </div>
                  </template>
                </Column>
                <Column header="Members in → out" style="width: 190px">
                  <template #body="{ data }">
                    <span class="numeric flow">
                      {{ formatNumber(data.stats?.membersIn) }}
                      <i class="pi pi-arrow-right" />
                      {{ formatNumber(data.stats?.membersOut) }}
                    </span>
                    <div v-if="data.stats?.inFlight" class="muted small">{{ formatNumber(data.stats.inFlight) }} in flight</div>
                  </template>
                </Column>
                <Column style="width: 170px" body-class="actions">
                  <template #body="{ data }">
                    <Button v-if="canBeHalted(data.status)" icon="pi pi-pause" text rounded size="small"
                      severity="warn" v-tooltip.top="'Halt'" aria-label="Halt" data-cy="halt" @click="halt(data)" />
                    <Button v-else-if="canBeResumed(data.status)" icon="pi pi-play" text rounded size="small"
                      severity="success" v-tooltip.top="'Resume'" aria-label="Resume" data-cy="resume"
                      @click="resume(data)" />
                    <Button v-else icon="pi pi-pause" text rounded size="small" disabled aria-label="Halt" />
                    <Button icon="pi pi-pencil" text rounded size="small" v-tooltip.top="'Edit in the sandbox'"
                      aria-label="Edit in the sandbox" data-cy="edit-pipeline" @click="edit(data)" />
                    <Button icon="pi pi-trash" text rounded size="small" severity="danger" v-tooltip.top="'Delete'"
                      aria-label="Delete" @click="remove(data)" />
                  </template>
                </Column>
                <template #expansion="{ data }">
                  <div class="expansion">
                    <PipelinePreview :pipeline="data.pipeline" :flow-id="`preview-${id}-${data.name}`" />
                    <CodeEditor :model-value="pipelineToYaml(data.pipeline)" language="yaml" readonly
                      min-height="230px" max-height="230px" />
                  </div>
                </template>
              </DataTable>
            </div>
          </TabPanel>

          <TabPanel value="catalog">
            <div class="catalog">
              <section v-for="group in catalogGroups" :key="group.kind" class="card catalog-group">
                <header class="catalog-header">
                  <span class="kind-dot" :style="{ background: group.info.color }" />
                  <h3>{{ group.info.plural }}</h3>
                  <span class="muted small">{{ group.components.length }} installed</span>
                </header>
                <p class="muted small">{{ group.info.description }}</p>
                <ul class="components">
                  <li v-for="component in group.components" :key="component.id" :style="{ '--kind': group.info.color }">
                    <span class="component-icon"><i :class="component.icon" /></span>
                    <div class="component-text">
                      <div class="component-title">
                        {{ component.label }}
                        <a v-if="component.docs" :href="component.docs" target="_blank" rel="noopener"
                          :aria-label="`Documentation of ${component.label}`"><i class="pi pi-external-link" /></a>
                      </div>
                      <div class="mono muted id">{{ component.id }}</div>
                      <div class="muted small">{{ component.description }}</div>
                    </div>
                  </li>
                </ul>
              </section>
            </div>
          </TabPanel>
        </TabPanels>
      </Tabs>
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

.pipeline-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pipeline-name .mono {
  font-weight: 600;
}

.chain {
  margin-top: 8px;
}

.status {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.status .pi {
  font-size: 10px;
}

.flow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.flow .pi {
  font-size: 10px;
  color: var(--muted);
}

:deep(.actions) {
  text-align: right;
  white-space: nowrap;
}

.expansion {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 16px;
  padding: 8px 4px;
}

.catalog {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.catalog-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.catalog-header h3 {
  font-size: 15px;
  flex: 1;
}

.catalog-group > p {
  margin: 6px 0 12px;
}

.components {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.components li {
  display: flex;
  gap: 10px;
}

.component-icon {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--kind) 13%, transparent);
  color: var(--kind);
}

.component-icon .pi {
  font-size: 13px;
}

.component-title {
  font-weight: 600;
  font-size: 13px;
}

.component-title .pi {
  font-size: 10px;
  margin-left: 4px;
}

.id {
  font-size: 11px;
}
</style>
