<script setup lang="ts">
import { computed } from 'vue'
import Tag from 'primevue/tag'
import EmptyState from '@/components/common/EmptyState.vue'
import { type BackgroundProcess, processInfo } from '@/domain/ldes'
import { formatDuration, formatNumber } from '@/utils/format'

/**
 * The steps of the fragmentation and maintenance jobs of an LDES server, as its batch metrics
 * tell since the server started.
 */
const props = defineProps<{ processes: BackgroundProcess[] }>()

const jobs = computed(() => {
  const byJob = new Map<string, BackgroundProcess[]>()
  props.processes.forEach((process) =>
    byJob.set(process.job, [...(byJob.get(process.job) ?? []), process]))
  return [...byJob.entries()].map(([job, steps]) => ({
    job,
    title: job.charAt(0).toUpperCase() + job.slice(1),
    steps: steps.map((step) => ({ ...step, info: processInfo(step) })),
  }))
})

function status(process: BackgroundProcess) {
  if (process.running) return { severity: 'info', icon: 'pi pi-spin pi-cog', label: 'Running' }
  if (process.failedRuns > 0) {
    return { severity: 'danger', icon: 'pi pi-times-circle', label: `${process.failedRuns} failed` }
  }
  return { severity: 'secondary', icon: 'pi pi-moon', label: 'Idle' }
}
</script>

<template>
  <div v-if="!processes.length" class="card">
    <EmptyState icon="pi pi-cog" title="No background job ran yet"
      description="The server fragments members shortly after they are ingested, and maintains its event streams on the maintenance schedule. Their steps show up here once they ran." />
  </div>
  <div v-else class="jobs">
    <section v-for="group in jobs" :key="group.job" class="job">
      <h2 class="job-title">{{ group.title }} <span class="muted small">job</span></h2>
      <div class="processes">
        <div v-for="process in group.steps" :key="process.step" class="card process"
          :class="{ running: process.running, failed: process.failedRuns > 0 && !process.running }"
          data-cy="process">
          <div class="process-header">
            <div class="process-icon"><i :class="process.info.icon" /></div>
            <div class="process-title">
              <h3>{{ process.info.name }}</h3>
              <p v-if="process.info.description" class="muted small">{{ process.info.description }}</p>
              <p v-else class="muted small mono">{{ process.step }}</p>
            </div>
            <Tag :severity="status(process).severity" :icon="status(process).icon"
              :value="status(process).label" />
          </div>
          <dl class="definition-list">
            <dt>Completed runs</dt>
            <dd class="numeric">{{ formatNumber(process.completedRuns) }}</dd>
            <dt>Failed runs</dt>
            <dd class="numeric" :class="{ bad: process.failedRuns > 0 }">{{ formatNumber(process.failedRuns) }}</dd>
            <dt>Average run</dt>
            <dd>{{ process.completedRuns ? formatDuration(process.averageMillis) : '–' }}</dd>
            <dt>Longest recent run</dt>
            <dd>{{ process.completedRuns ? formatDuration(process.longestMillis) : '–' }}</dd>
          </dl>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.jobs {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.job-title {
  font-size: 15px;
  margin: 0 0 12px;
}

.processes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}

.process {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.process.failed {
  border-color: var(--p-red-200);
}

.app-dark .process.failed {
  border-color: color-mix(in srgb, var(--p-red-500) 40%, transparent);
}

.process-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.process-icon {
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--p-content-hover-background);
  color: var(--muted);
}

.running .process-icon {
  background: var(--p-sky-50);
  color: var(--p-sky-600);
}

.app-dark .running .process-icon {
  background: color-mix(in srgb, var(--p-sky-500) 14%, transparent);
  color: var(--p-sky-300);
}

.process-title {
  flex: 1;
  min-width: 0;
}

.process-title h3 {
  font-size: 14.5px;
}

.process-title p {
  margin: 3px 0 0;
}

.bad {
  color: var(--p-red-600);
  font-weight: 600;
}

.app-dark .bad {
  color: var(--p-red-300);
}
</style>
