<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import { useToast } from 'primevue/usetoast'
import type { ApiMode } from '@/api'
import { useInstancesStore } from '@/stores/instances'
import { useModeStore } from '@/stores/mode'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const ui = useUiStore()
const instances = useInstancesStore()
const mode = useModeStore()
const toast = useToast()

const modes: { value: ApiMode; label: string; icon: string; tooltip: string }[] = [
  {
    value: 'demo',
    label: 'Demo',
    icon: 'pi pi-sparkles',
    tooltip: 'Explore LDES-admin on sample data; nothing leaves your browser',
  },
  {
    value: 'production',
    label: 'Production',
    icon: 'pi pi-server',
    tooltip: 'Manage real LDES servers and LDIOs through the LDES-admin backend',
  },
]

async function switchMode(next: ApiMode) {
  if (next === mode.mode) return
  await mode.switchTo(next)
  if (next === 'demo') {
    toast.add({ severity: 'info', summary: 'Demo mode', detail: 'You are looking at sample data.', life: 3500 })
  } else if (mode.backendUp) {
    toast.add({ severity: 'success', summary: 'Production mode', detail: 'Connected to the LDES-admin backend.', life: 3500 })
  } else {
    toast.add({
      severity: 'warn',
      summary: 'Production mode',
      detail: 'The LDES-admin backend does not answer. Start it, or switch back to demo mode.',
      life: 7000,
    })
  }
}

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
      <span v-if="mode.backendUnreachable" v-tooltip.bottom="'The LDES-admin backend does not answer'"
        class="backend-down" data-cy="backend-down" role="status">
        <i class="pi pi-exclamation-triangle" /> Backend unreachable
        <Button label="Retry" text size="small" class="retry" @click="mode.checkBackend()" />
      </span>
      <div class="mode-switch" :class="mode.mode" role="radiogroup" aria-label="Data mode" data-cy="mode-switch">
        <button v-for="option in modes" :key="option.value" v-tooltip.bottom="option.tooltip" type="button"
          role="radio" :aria-checked="mode.mode === option.value" class="mode-option"
          :class="[option.value, { active: mode.mode === option.value }]" :data-cy="`mode-${option.value}`"
          @click="switchMode(option.value)">
          <span v-if="mode.mode === option.value && option.value === 'demo'" class="pulse" />
          <i v-else :class="option.icon" />
          {{ option.label }}
        </button>
      </div>
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

.mode-switch {
  display: inline-flex;
  padding: 3px;
  gap: 2px;
  border-radius: 999px;
  background: var(--card-bg);
  border: 1px solid var(--card-border);
}

.mode-option {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: none;
  color: var(--muted);
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.mode-option .pi {
  font-size: 11px;
}

.mode-option:hover:not(.active) {
  color: var(--text);
}

.mode-option:focus-visible {
  outline: 2px solid var(--p-primary-color);
  outline-offset: 1px;
}

.mode-option.demo.active {
  color: var(--p-amber-700);
  background: var(--p-amber-50);
  border-color: var(--p-amber-200);
}

.mode-option.production.active {
  color: var(--p-primary-700);
  background: var(--p-primary-50);
  border-color: var(--p-primary-200);
}

.app-dark .mode-option.demo.active {
  color: var(--p-amber-300);
  background: color-mix(in srgb, var(--p-amber-500) 12%, transparent);
  border-color: color-mix(in srgb, var(--p-amber-500) 30%, transparent);
}

.app-dark .mode-option.production.active {
  color: var(--p-primary-200);
  background: color-mix(in srgb, var(--p-primary-500) 14%, transparent);
  border-color: color-mix(in srgb, var(--p-primary-500) 32%, transparent);
}

.backend-down {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--p-red-600);
}

.app-dark .backend-down {
  color: var(--p-red-300);
}

.retry {
  padding: 2px 6px !important;
  font-size: 12px !important;
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
