<script setup lang="ts">
import { computed } from 'vue'
import type { HealthStatus } from '@/domain/instance'

const props = defineProps<{ status: HealthStatus | undefined }>()

const presentation = computed(() => {
  switch (props.status) {
    case 'UP':
      return { label: 'Up', icon: 'pi pi-check-circle', tone: 'up' }
    case 'DOWN':
      return { label: 'Down', icon: 'pi pi-times-circle', tone: 'down' }
    case 'UNKNOWN':
      return { label: 'Unknown', icon: 'pi pi-question-circle', tone: 'unknown' }
    default:
      return { label: 'Checking', icon: 'pi pi-spin pi-spinner', tone: 'unknown' }
  }
})
</script>

<template>
  <span class="health" :class="presentation.tone" data-cy="health">
    <i :class="presentation.icon" />
    {{ presentation.label }}
  </span>
</template>

<style scoped>
.health {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px 2px 7px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  white-space: nowrap;
}

.health .pi {
  font-size: 12px;
}

.up {
  color: var(--p-green-700);
  background: var(--p-green-50);
}

.down {
  color: var(--p-red-700);
  background: var(--p-red-50);
}

.unknown {
  color: var(--p-surface-600);
  background: var(--p-surface-100);
}

.app-dark .up {
  color: var(--p-green-300);
  background: color-mix(in srgb, var(--p-green-500) 14%, transparent);
}

.app-dark .down {
  color: var(--p-red-300);
  background: color-mix(in srgb, var(--p-red-500) 14%, transparent);
}

.app-dark .unknown {
  color: var(--p-surface-300);
  background: var(--p-surface-800);
}
</style>
