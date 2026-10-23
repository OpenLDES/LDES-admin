<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import { useConfirm } from 'primevue/useconfirm'
import PageHeader from '@/components/common/PageHeader.vue'
import HealthBadge from '@/components/common/HealthBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import RegisterInstanceDialog from '@/components/instances/RegisterInstanceDialog.vue'
import StartLocalInstanceDialog from '@/components/instances/StartLocalInstanceDialog.vue'
import { api } from '@/api'
import { useAction, useAsync } from '@/composables/useAsync'
import type { Instance, InstanceType } from '@/domain/instance'
import type { LdesServerSummary, LdioSummary } from '@/domain/dashboard'
import { useInstancesStore } from '@/stores/instances'
import { formatCompact, formatRelative, shortUrl } from '@/utils/format'

/**
 * The LDES servers or LDIOs, as cards with their health and activity.
 */
const props = defineProps<{ type: InstanceType }>()

const router = useRouter()
const confirm = useConfirm()
const instances = useInstancesStore()
const { run } = useAction()
const { data: overview, reload } = useAsync(() => api.getDashboard())
const showRegister = ref(false)
const showStartLocal = ref(false)

const isLdio = computed(() => props.type === 'LDIO')
const label = computed(() => (isLdio.value ? 'LDIO' : 'LDES server'))
const summaries = computed<(LdesServerSummary | LdioSummary)[]>(() =>
  (isLdio.value ? overview.value?.ldios : overview.value?.ldesServers) ?? [])

function instanceOf(summary: LdesServerSummary | LdioSummary): Instance {
  return 'server' in summary ? summary.server : summary.ldio
}

function open(instance: Instance) {
  router.push(isLdio.value ? `/ldios/${instance.id}` : `/ldes-servers/${instance.id}`)
}

function remove(instance: Instance) {
  const local = instance.origin === 'LOCAL'
  confirm.require({
    header: local ? `Stop ${instance.name}?` : `Remove ${instance.name}?`,
    message: local
      ? `The local ${label.value} and its container are stopped and removed, including all its data.`
      : `LDES-admin forgets this ${label.value}. The ${label.value} itself keeps running.`,
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', text: true },
    acceptProps: { label: local ? 'Stop and remove' : 'Remove', severity: 'danger' },
    accept: async () => {
      await run(() => instances.remove(instance), `${instance.name} removed`)
      reload()
    },
  })
}

function pipelineCount(summary: LdioSummary, ...statuses: string[]) {
  return summary.pipelines.filter((p) => statuses.includes(p.status)).length
}
</script>

<template>
  <div class="page">
    <PageHeader :title="isLdio ? 'Linked Data Interaction Orchestrators' : 'LDES servers'"
      :subtitle="isLdio ? 'Orchestrators running pipelines that feed and consume LDES' : 'OpenLDES servers publishing Linked Data Event Streams'"
      :icon="isLdio ? 'pi pi-share-alt' : 'pi pi-server'">
      <template #actions>
        <Button :label="`Start local ${label}`" icon="pi pi-play" severity="secondary" outlined
          data-cy="start-local" @click="showStartLocal = true" />
        <Button :label="`Add existing ${label}`" icon="pi pi-plus" data-cy="add-instance"
          @click="showRegister = true" />
      </template>
    </PageHeader>

    <div v-if="!overview" class="card-grid">
      <Skeleton v-for="n in 3" :key="n" height="210px" border-radius="12px" />
    </div>
    <div v-else-if="summaries.length" class="card-grid">
      <article v-for="summary in summaries" :key="instanceOf(summary).id" class="card clickable instance"
        data-cy="instance-card" @click="open(instanceOf(summary))">
        <header class="instance-header">
          <div class="instance-icon" :class="{ local: instanceOf(summary).origin === 'LOCAL' }">
            <i :class="isLdio ? 'pi pi-share-alt' : 'pi pi-server'" />
          </div>
          <div class="instance-title">
            <h2>{{ instanceOf(summary).name }}</h2>
            <span class="muted small truncate">{{ shortUrl(instanceOf(summary).url) }}</span>
          </div>
          <HealthBadge :status="summary.health.status" />
        </header>
        <div class="row-wrap tags">
          <span class="chip">
            <i :class="instanceOf(summary).origin === 'LOCAL' ? 'pi pi-desktop' : 'pi pi-link'" />
            {{ instanceOf(summary).origin === 'LOCAL' ? 'Local container' : 'Registered' }}
          </span>
          <span v-if="instanceOf(summary).sandbox" class="chip"><i class="pi pi-box" />Sandbox</span>
          <span class="chip"><i class="pi pi-calendar" />Added {{ formatRelative(instanceOf(summary).registeredAt) }}</span>
        </div>

        <div v-if="summary.health.status !== 'UP'" class="unreachable">
          <i class="pi pi-times-circle" /> {{ shortUrl(instanceOf(summary).url) }} does not respond
        </div>
        <div v-else-if="'server' in summary" class="metrics">
          <div><span class="metric">{{ summary.streams.length }}</span><span class="label">Streams</span></div>
          <div>
            <span class="metric">{{ summary.streams.reduce((n, s) => n + s.views.length, 0) }}</span>
            <span class="label">Views</span>
          </div>
          <div><span class="metric">{{ formatCompact(summary.ingestedMembers) }}</span><span class="label">Members</span></div>
        </div>
        <div v-else class="metrics">
          <div><span class="metric">{{ pipelineCount(summary, 'RUNNING') }}</span><span class="label">Running</span></div>
          <div><span class="metric">{{ pipelineCount(summary, 'HALTED') }}</span><span class="label">Halted</span></div>
          <div><span class="metric">{{ formatCompact(summary.processedMembers) }}</span><span class="label">Processed</span></div>
        </div>

        <div v-if="summary.problems.length" class="problems">
          <i class="pi pi-exclamation-triangle" />
          <span>{{ summary.problems[0] }}</span>
        </div>

        <footer class="instance-footer">
          <Button :label="instanceOf(summary).origin === 'LOCAL' ? 'Stop' : 'Remove'"
            :icon="instanceOf(summary).origin === 'LOCAL' ? 'pi pi-stop' : 'pi pi-trash'" severity="danger"
            text size="small" data-cy="remove-instance" @click.stop="remove(instanceOf(summary))" />
          <Button label="Open" icon="pi pi-arrow-right" icon-pos="right" text size="small"
            @click.stop="open(instanceOf(summary))" />
        </footer>
      </article>
    </div>
    <div v-else class="card">
      <EmptyState :icon="isLdio ? 'pi pi-share-alt' : 'pi pi-server'" :title="`No ${label}s yet`"
        :description="`Add a ${label} that already runs somewhere, or start one locally to experiment.`">
        <Button :label="`Add existing ${label}`" icon="pi pi-plus" @click="showRegister = true" />
      </EmptyState>
    </div>

    <RegisterInstanceDialog v-model:visible="showRegister" :type="type" @registered="open" />
    <StartLocalInstanceDialog v-model:visible="showStartLocal" :type="type" @started="open" />
  </div>
</template>

<style scoped>
.instance {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.instance-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.instance-icon {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--p-primary-50);
  color: var(--p-primary-600);
}

.instance-icon.local {
  background: var(--p-violet-50);
  color: var(--p-violet-600);
}

.app-dark .instance-icon {
  background: color-mix(in srgb, var(--p-primary-500) 14%, transparent);
  color: var(--p-primary-300);
}

.app-dark .instance-icon.local {
  background: color-mix(in srgb, var(--p-violet-500) 16%, transparent);
  color: var(--p-violet-300);
}

.instance-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.instance-title h2 {
  font-size: 16px;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid var(--card-border);
  border-bottom: 1px solid var(--card-border);
}

.metrics > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.unreachable {
  padding: 14px;
  border-radius: 8px;
  color: var(--p-red-700);
  background: var(--p-red-50);
  font-size: 13px;
}

.app-dark .unreachable {
  color: var(--p-red-300);
  background: color-mix(in srgb, var(--p-red-500) 12%, transparent);
}

.problems {
  display: flex;
  gap: 8px;
  font-size: 12.5px;
  color: var(--p-amber-700);
}

.app-dark .problems {
  color: var(--p-amber-300);
}

.problems .pi {
  margin-top: 2px;
}

.instance-footer {
  display: flex;
  justify-content: space-between;
  margin: -6px -8px -8px;
}
</style>
