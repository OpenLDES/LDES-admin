<script setup lang="ts">
import ProgressBar from 'primevue/progressbar'
import Tag from 'primevue/tag'
import type { BackgroundProcess, ProcessStatus } from '@/domain/ldes'
import { formatDateTime, formatDuration, formatNumber, formatRelative } from '@/utils/format'

defineProps<{ processes: BackgroundProcess[] }>()

const status: Record<ProcessStatus, { severity: string; icon: string; label: string }> = {
  IDLE: { severity: 'secondary', icon: 'pi pi-moon', label: 'Idle' },
  RUNNING: { severity: 'info', icon: 'pi pi-spin pi-cog', label: 'Running' },
  FAILED: { severity: 'danger', icon: 'pi pi-times-circle', label: 'Failed' },
}
</script>

<template>
  <div class="processes">
    <div v-for="process in processes" :key="process.id" class="card process" :class="process.status.toLowerCase()"
      data-cy="process">
      <div class="process-header">
        <div class="process-icon"><i :class="process.icon" /></div>
        <div class="process-title">
          <h3>{{ process.name }}</h3>
          <p class="muted small">{{ process.description }}</p>
        </div>
        <Tag :severity="status[process.status].severity" :icon="status[process.status].icon"
          :value="status[process.status].label" />
      </div>
      <ProgressBar v-if="process.status === 'RUNNING'" :value="process.progress" class="progress">
        {{ process.progress }}%
      </ProgressBar>
      <div v-if="process.message" class="failure mono">{{ process.message }}</div>
      <dl class="definition-list">
        <dt>Schedule</dt>
        <dd><code>{{ process.schedule }}</code></dd>
        <dt>Last run</dt>
        <dd>{{ formatRelative(process.lastRun) }} · took {{ formatDuration(process.lastDurationMs) }}</dd>
        <dt>Next run</dt>
        <dd>{{ formatDateTime(process.nextRun) }}</dd>
        <dt>Processed</dt>
        <dd class="numeric">{{ formatNumber(process.processedItems) }}</dd>
      </dl>
    </div>
  </div>
</template>

<style scoped>
.processes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
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

.progress {
  height: 18px;
  font-size: 11px;
}

.failure {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--p-red-50);
  color: var(--p-red-700);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.app-dark .failure {
  background: color-mix(in srgb, var(--p-red-500) 12%, transparent);
  color: var(--p-red-300);
}
</style>
