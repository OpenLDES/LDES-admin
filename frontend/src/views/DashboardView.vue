<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import PageHeader from '@/components/common/PageHeader.vue'
import StatTile from '@/components/common/StatTile.vue'
import HealthBadge from '@/components/common/HealthBadge.vue'
import PipelineStatusTag from '@/components/common/PipelineStatusTag.vue'
import ThroughputChart from '@/components/dashboard/ThroughputChart.vue'
import ActivityFeed from '@/components/dashboard/ActivityFeed.vue'
import RegisterInstanceDialog from '@/components/instances/RegisterInstanceDialog.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SandboxEnvironmentPanel from '@/components/sandbox/SandboxEnvironmentPanel.vue'
import { api } from '@/api'
import { useAsync } from '@/composables/useAsync'
import type { Instance, InstanceType } from '@/domain/instance'
import { useSandboxEnvironmentStore } from '@/stores/sandboxEnvironment'
import { formatCompact, formatNumber, shortUrl } from '@/utils/format'

/** How often the dashboard refreshes itself */
const REFRESH_INTERVAL = 30_000

const router = useRouter()
const environment = useSandboxEnvironmentStore()
const { data: overview, reload: reloadOverview } = useAsync(() => api.getDashboard())
const { data: throughput, reload: reloadThroughput } = useAsync(() => api.getThroughput())
const { data: activity, reload: reloadActivity } = useAsync(() => api.getActivity())

function refresh() {
  reloadOverview()
  reloadThroughput()
  reloadActivity()
}

// Starting or stopping the sandbox adds or removes instances
watch(() => environment.sandbox?.state, (state, previous) => {
  if (previous !== undefined && state !== previous) refresh()
})

let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => (timer = setInterval(refresh, REFRESH_INTERVAL)))
onBeforeUnmount(() => clearInterval(timer))

const registerType = ref<InstanceType>('LDES_SERVER')
const showRegister = ref(false)

function openRegister(type: InstanceType) {
  registerType.value = type
  showRegister.value = true
}

function onRegistered(instance: Instance) {
  router.push(instance.type === 'LDIO' ? `/ldios/${instance.id}` : `/ldes-servers/${instance.id}`)
}

const kpis = computed(() => {
  if (!overview.value) return undefined
  const servers = overview.value.ldesServers
  const ldios = overview.value.ldios
  const serversUp = servers.filter((s) => s.health.status === 'UP').length
  const ldiosUp = ldios.filter((l) => l.health.status === 'UP').length
  const pipelines = ldios.flatMap((l) => l.pipelines)
  const running = pipelines.filter((p) => p.status === 'RUNNING').length
  const halted = pipelines.filter((p) => p.status === 'HALTED').length
  const starting = pipelines.filter((p) => ['INIT', 'STARTING', 'RESUMING'].includes(p.status)).length
  // Series start when LDES-admin started sampling a server, so add them up per hour
  const perHour = new Map<string, number>()
  ;(throughput.value ?? []).forEach((series) => series.points.forEach((point) =>
    perHour.set(point.time, (perHour.get(point.time) ?? 0) + point.members)))
  const hourly = [...perHour.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([, members]) => members)
  const problems = servers.reduce((n, s) => n + s.problems.length + (s.health.status === 'DOWN' ? 1 : 0), 0)
    + ldios.reduce((n, l) => n + l.problems.length + (l.health.status === 'DOWN' ? 1 : 0), 0)
  const backlog = servers.flatMap((s) => s.streams).reduce((n, s) => n + s.backlog, 0)
  return {
    servers: { value: `${serversUp} of ${servers.length}`, caption: servers.length - serversUp ? `${servers.length - serversUp} down` : 'All healthy' },
    ldios: { value: `${ldiosUp} of ${ldios.length}`, caption: ldios.length - ldiosUp ? `${ldios.length - ldiosUp} down` : 'All healthy' },
    pipelines: {
      value: `${running} of ${pipelines.length}`,
      caption: [halted && `${halted} halted`, starting && `${starting} starting`].filter(Boolean).join(', ') || 'All running',
    },
    ingested: { value: formatCompact(hourly.reduce((a, b) => a + b, 0)), trend: hourly },
    problems: { value: String(problems), backlog },
  }
})
</script>

<template>
  <div class="page">
    <PageHeader title="Dashboard" subtitle="Health and activity of your OpenLDES servers and pipelines">
      <template #actions>
        <Button label="Add LDES server" icon="pi pi-server" severity="secondary" outlined
          @click="openRegister('LDES_SERVER')" />
        <Button label="Add LDIO" icon="pi pi-share-alt" severity="secondary" outlined
          @click="openRegister('LDIO')" />
        <Button label="Open sandbox" icon="pi pi-sitemap" @click="router.push('/sandbox')" />
      </template>
    </PageHeader>

    <section class="kpis">
      <template v-if="kpis">
        <StatTile label="LDES servers up" :value="kpis.servers.value" :caption="kpis.servers.caption"
          icon="pi pi-server" :tone="kpis.servers.caption === 'All healthy' ? 'good' : 'warning'" to="/ldes-servers" />
        <StatTile label="LDIOs up" :value="kpis.ldios.value" :caption="kpis.ldios.caption" icon="pi pi-share-alt"
          to="/ldios" />
        <StatTile label="Pipelines running" :value="kpis.pipelines.value" :caption="kpis.pipelines.caption"
          icon="pi pi-play-circle" to="/ldios" />
        <StatTile label="Members ingested, 24 h" :value="kpis.ingested.value" caption="All LDES servers"
          icon="pi pi-download" :trend="kpis.ingested.trend" to="/ldes-servers" />
        <StatTile label="Needs attention" :value="kpis.problems.value" icon="pi pi-exclamation-triangle"
          :tone="Number(kpis.problems.value) > 0 ? 'critical' : 'good'"
          :caption="`${formatCompact(kpis.problems.backlog)} members waiting to be paginated`" />
      </template>
      <template v-else>
        <Skeleton v-for="n in 5" :key="n" height="112px" border-radius="12px" />
      </template>
    </section>

    <section class="grid-main-side">
      <ThroughputChart v-if="throughput" :series="throughput" />
      <Skeleton v-else height="360px" border-radius="12px" />
      <div class="card activity">
        <div class="card-header">
          <div>
            <div class="card-title">Recent activity</div>
            <div class="card-subtitle">Status changes and problems</div>
          </div>
        </div>
        <div class="activity-scroll">
          <ActivityFeed v-if="activity?.length" :events="activity" />
          <EmptyState v-else-if="activity" icon="pi pi-history" title="Nothing happened yet"
            description="Actions taken through LDES-admin, and instances going down or coming back, show up here." />
        </div>
      </div>
    </section>

    <section class="card">
      <SandboxEnvironmentPanel />
    </section>

    <section class="grid-2">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">LDES servers</div>
            <div class="card-subtitle">Event streams and their members</div>
          </div>
          <RouterLink to="/ldes-servers" class="small">All servers <i class="pi pi-arrow-right small" /></RouterLink>
        </div>
        <div v-if="overview" class="rows">
          <RouterLink v-for="summary in overview.ldesServers" :key="summary.server.id"
            :to="`/ldes-servers/${summary.server.id}`" class="list-row" data-cy="dashboard-server">
            <div class="row-main">
              <div class="row-title">
                {{ summary.server.name }}
                <span v-if="summary.server.sandbox" class="chip"><i class="pi pi-box" />Sandbox</span>
              </div>
              <div class="row-sub">{{ shortUrl(summary.server.url) }}</div>
            </div>
            <div class="row-figures">
              <div v-if="summary.health.status === 'UP'" class="figure">
                <span class="figure-value">{{ summary.streams.length }}</span>
                <span class="figure-label">streams</span>
              </div>
              <div v-if="summary.health.status === 'UP'" class="figure">
                <span class="figure-value">{{ formatCompact(summary.ingestedMembers) }}</span>
                <span class="figure-label">members</span>
              </div>
              <i v-if="summary.problems.length" v-tooltip.top="summary.problems.join('\n')"
                class="pi pi-exclamation-triangle problem" aria-label="Has problems" />
              <HealthBadge :status="summary.health.status" />
            </div>
          </RouterLink>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">Pipelines</div>
            <div class="card-subtitle">Members in and out, per LDIO</div>
          </div>
          <RouterLink to="/ldios" class="small">All LDIOs <i class="pi pi-arrow-right small" /></RouterLink>
        </div>
        <div v-if="overview" class="ldios">
          <div v-for="summary in overview.ldios" :key="summary.ldio.id" class="ldio-group">
            <RouterLink :to="`/ldios/${summary.ldio.id}`" class="ldio-heading">
              <span class="row-title">{{ summary.ldio.name }}</span>
              <span v-if="summary.ldio.sandbox" class="chip"><i class="pi pi-box" />Sandbox</span>
              <span class="spacer" />
              <HealthBadge :status="summary.health.status" />
            </RouterLink>
            <div v-for="pipeline in summary.pipelines" :key="pipeline.name" class="pipeline-row">
              <PipelineStatusTag :status="pipeline.status" />
              <span class="pipeline-name truncate">{{ pipeline.name }}</span>
              <span class="pipeline-flow numeric">
                {{ formatNumber(pipeline.membersIn) }}
                <i class="pi pi-arrow-right" />
                {{ formatNumber(pipeline.membersOut) }}
              </span>
            </div>
            <div v-if="!summary.pipelines.length" class="muted small empty-line">No pipelines yet</div>
          </div>
        </div>
      </div>
    </section>

    <RegisterInstanceDialog v-model:visible="showRegister" :type="registerType" @registered="onRegistered" />
  </div>
</template>

<style scoped>
.page > section + section {
  margin-top: 20px;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.activity {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* Scrolls instead of stretching the row: the chart decides the height */
.activity-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  contain: size;
  margin-right: -8px;
  padding-right: 8px;
}

.rows {
  display: flex;
  flex-direction: column;
  margin: 0 -10px;
}

.list-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 11px 10px;
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
}

.list-row:hover {
  background: var(--hover-bg);
  text-decoration: none;
}

.row-main {
  flex: 1;
  min-width: 0;
}

.row-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--text);
}

.row-sub {
  font-size: 12.5px;
  color: var(--muted);
  margin-top: 2px;
}

.row-figures {
  display: flex;
  align-items: center;
  gap: 18px;
}

.figure {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}

.figure-value {
  font-weight: 600;
  color: var(--text);
}

.figure-label {
  font-size: 11.5px;
  color: var(--muted);
}

.problem {
  color: var(--p-amber-500);
}

.ldios {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ldio-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--card-border);
  color: inherit;
  text-decoration: none;
}

.ldio-heading:hover .row-title {
  color: var(--p-primary-color);
}

.pipeline-row {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 7px 0;
  font-size: 13px;
}

.pipeline-name {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.pipeline-flow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 12.5px;
}

.pipeline-flow .pi {
  font-size: 10px;
}

.empty-line {
  padding: 8px 0;
}
</style>
