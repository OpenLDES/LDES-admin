<script setup lang="ts">
import { computed } from 'vue'
import Tag from 'primevue/tag'
import type { PipelineStatus } from '@/domain/ldio'

const props = defineProps<{ status: PipelineStatus }>()

const presentation = computed(() => {
  switch (props.status) {
    case 'RUNNING':
      return { severity: 'success', icon: 'pi pi-play-circle', label: 'Running' }
    case 'HALTED':
      return { severity: 'warn', icon: 'pi pi-pause-circle', label: 'Halted' }
    case 'INIT':
      return { severity: 'info', icon: 'pi pi-spin pi-spinner', label: 'Initialising' }
    case 'STARTING':
      return { severity: 'info', icon: 'pi pi-spin pi-spinner', label: 'Starting' }
    case 'RESUMING':
      return { severity: 'info', icon: 'pi pi-spin pi-spinner', label: 'Resuming' }
    case 'STOPPING':
      return { severity: 'secondary', icon: 'pi pi-spin pi-spinner', label: 'Stopping' }
    default:
      return { severity: 'secondary', icon: 'pi pi-stop-circle', label: 'Stopped' }
  }
})
</script>

<template>
  <Tag :severity="presentation.severity" :icon="presentation.icon" :value="presentation.label"
    class="pipeline-status" data-cy="pipeline-status" />
</template>

<style scoped>
.pipeline-status {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
</style>
