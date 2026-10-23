<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import { useInstancesStore } from '@/stores/instances'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const ui = useUiStore()
const instances = useInstancesStore()

interface Crumb {
  label: string
  to?: string
}

const crumbs = computed<Crumb[]>(() => {
  const id = route.params.id as string | undefined
  const name = id ? instances.find(id)?.name ?? '…' : undefined
  switch (route.name) {
    case 'dashboard':
      return [{ label: 'Dashboard' }]
    case 'ldes-servers':
      return [{ label: 'LDES servers' }]
    case 'ldes-server':
      return [{ label: 'LDES servers', to: '/ldes-servers' }, { label: name! }]
    case 'event-stream':
      return [
        { label: 'LDES servers', to: '/ldes-servers' },
        { label: name!, to: `/ldes-servers/${id}` },
        { label: route.params.collection as string },
      ]
    case 'ldios':
      return [{ label: 'LDIOs' }]
    case 'ldio':
      return [{ label: 'LDIOs', to: '/ldios' }, { label: name! }]
    case 'sandbox':
      return [{ label: 'Sandbox' }]
    default:
      return [{ label: route.meta.title ?? '' }]
  }
})
</script>

<template>
  <header class="topbar">
    <nav class="breadcrumb" aria-label="Breadcrumb">
      <template v-for="(crumb, index) in crumbs" :key="index">
        <i v-if="index > 0" class="pi pi-angle-right separator" />
        <RouterLink v-if="crumb.to" :to="crumb.to" class="crumb">{{ crumb.label }}</RouterLink>
        <span v-else class="crumb current">{{ crumb.label }}</span>
      </template>
    </nav>
    <div class="actions">
      <span v-tooltip.bottom="'No backend calls: the API runs on in-memory sample data'" class="data-mode">
        <span class="pulse" /> Sample data
      </span>
      <Button
        v-tooltip.bottom="ui.dark ? 'Light mode' : 'Dark mode'"
        :icon="ui.dark ? 'pi pi-sun' : 'pi pi-moon'"
        severity="secondary"
        text
        rounded
        aria-label="Toggle dark mode"
        data-cy="toggle-dark"
        @click="ui.toggleDark()"
      />
      <Avatar label="SD" shape="circle" class="avatar" />
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 5;
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px 0 32px;
  background: color-mix(in srgb, var(--page-bg) 82%, transparent);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--card-border);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 13.5px;
}

.separator {
  font-size: 11px;
  color: var(--muted);
}

.crumb {
  color: var(--muted);
  white-space: nowrap;
}

.crumb.current {
  color: var(--text);
  font-weight: 600;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.data-mode {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: var(--p-amber-700);
  background: var(--p-amber-50);
  border: 1px solid var(--p-amber-200);
}

.app-dark .data-mode {
  color: var(--p-amber-300);
  background: color-mix(in srgb, var(--p-amber-500) 12%, transparent);
  border-color: color-mix(in srgb, var(--p-amber-500) 30%, transparent);
}

.pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--p-amber-500);
  box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.5);
  animation: pulse 2.4s infinite;
}

@keyframes pulse {
  70% {
    box-shadow: 0 0 0 7px rgba(245, 158, 11, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 158, 11, 0);
  }
}

.avatar {
  background: var(--p-primary-100);
  color: var(--p-primary-800);
  font-size: 12px;
  font-weight: 600;
}

.app-dark .avatar {
  background: var(--p-primary-900);
  color: var(--p-primary-200);
}
</style>
