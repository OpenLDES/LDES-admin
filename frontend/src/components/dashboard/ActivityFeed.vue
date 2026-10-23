<script setup lang="ts">
import type { ActivityEvent, ActivitySeverity } from '@/domain/dashboard'
import { formatRelative } from '@/utils/format'

defineProps<{ events: ActivityEvent[] }>()

const icons: Record<ActivitySeverity, string> = {
  info: 'pi pi-info-circle',
  success: 'pi pi-check-circle',
  warn: 'pi pi-exclamation-triangle',
  error: 'pi pi-times-circle',
}

const severityLabel: Record<ActivitySeverity, string> = {
  info: 'Info',
  success: 'Success',
  warn: 'Warning',
  error: 'Error',
}

function link(event: ActivityEvent) {
  return event.source.type === 'LDIO' ? `/ldios/${event.source.id}` : `/ldes-servers/${event.source.id}`
}
</script>

<template>
  <ul class="feed">
    <li v-for="event in events" :key="event.id" class="event" :class="event.severity">
      <span class="event-icon" :title="severityLabel[event.severity]">
        <i :class="icons[event.severity]" :aria-label="severityLabel[event.severity]" />
      </span>
      <div class="event-body">
        <div class="event-title">{{ event.title }}</div>
        <div v-if="event.detail" class="event-detail">{{ event.detail }}</div>
        <div class="event-meta">
          <RouterLink :to="link(event)">{{ event.source.name }}</RouterLink>
          <span>·</span>
          <span>{{ formatRelative(event.time) }}</span>
        </div>
      </div>
    </li>
  </ul>
</template>

<style scoped>
.feed {
  list-style: none;
  margin: 0;
  padding: 0;
}

.event {
  display: flex;
  gap: 12px;
  padding: 10px 0;
}

.event + .event {
  border-top: 1px solid var(--card-border);
}

.event-icon {
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 8px;
  display: grid;
  place-items: center;
}

.event-icon .pi {
  font-size: 13px;
}

.info .event-icon {
  background: var(--p-sky-50);
  color: var(--p-sky-700);
}

.success .event-icon {
  background: var(--p-green-50);
  color: var(--p-green-700);
}

.warn .event-icon {
  background: var(--p-amber-50);
  color: var(--p-amber-700);
}

.error .event-icon {
  background: var(--p-red-50);
  color: var(--p-red-700);
}

.app-dark .info .event-icon {
  background: color-mix(in srgb, var(--p-sky-500) 14%, transparent);
  color: var(--p-sky-300);
}

.app-dark .success .event-icon {
  background: color-mix(in srgb, var(--p-green-500) 14%, transparent);
  color: var(--p-green-300);
}

.app-dark .warn .event-icon {
  background: color-mix(in srgb, var(--p-amber-500) 14%, transparent);
  color: var(--p-amber-300);
}

.app-dark .error .event-icon {
  background: color-mix(in srgb, var(--p-red-500) 14%, transparent);
  color: var(--p-red-300);
}

.event-body {
  min-width: 0;
}

.event-title {
  font-weight: 500;
  font-size: 13px;
}

.event-detail {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--muted);
  overflow-wrap: anywhere;
}

.event-meta {
  display: flex;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}
</style>
