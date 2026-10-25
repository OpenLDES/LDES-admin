<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import AppLogo from './AppLogo.vue'
import { useInstancesStore } from '@/stores/instances'
import { useModeStore } from '@/stores/mode'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const ui = useUiStore()
const { sidebarCollapsed } = storeToRefs(ui)
const instances = useInstancesStore()
const mode = useModeStore()

interface NavItem {
  label: string
  icon: string
  to: string
  count?: number
  match: (path: string) => boolean
}

const sections = computed<{ title: string; items: NavItem[] }[]>(() => [
  {
    title: 'Overview',
    items: [{ label: 'Dashboard', icon: 'pi pi-objects-column', to: '/', match: (path: string) => path === '/' }],
  },
  {
    title: 'Manage',
    items: [
      {
        label: 'LDES servers',
        icon: 'pi pi-server',
        to: '/ldes-servers',
        count: instances.ldesServers.length,
        match: (path: string) => path.startsWith('/ldes-servers'),
      },
      {
        label: 'LDIOs',
        icon: 'pi pi-share-alt',
        to: '/ldios',
        count: instances.ldios.length,
        match: (path: string) => path.startsWith('/ldios'),
      },
    ],
  },
  {
    title: 'Experiment',
    items: [
      {
        label: 'Sandbox',
        icon: 'pi pi-sitemap',
        to: '/sandbox',
        match: (path: string) => path.startsWith('/sandbox'),
      },
    ],
  },
])

const links = [
  { label: 'OpenLDES', href: 'https://openldes.org/' },
  { label: 'LDES Server docs', href: 'https://openldes.github.io/LDESServer/4.2.1/' },
  { label: 'LDIO docs', href: 'https://openldes.github.io/Linked-Data-Interactions/3.2.0-SNAPSHOT/' },
]
</script>

<template>
  <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }" data-cy="sidebar">
    <RouterLink to="/" class="brand">
      <AppLogo :size="34" />
      <div v-if="!sidebarCollapsed" class="brand-text">
        <span class="brand-name">LDES-admin</span>
        <span class="brand-tagline">OpenLDES control centre</span>
      </div>
    </RouterLink>

    <nav class="nav">
      <div v-for="section in sections" :key="section.title" class="nav-section">
        <div v-if="!sidebarCollapsed" class="nav-section-title">{{ section.title }}</div>
        <RouterLink
          v-for="item in section.items"
          :key="item.to"
          v-tooltip.right="sidebarCollapsed ? item.label : undefined"
          :to="item.to"
          class="nav-item"
          :class="{ active: item.match(route.path) }"
        >
          <i :class="item.icon" />
          <span v-if="!sidebarCollapsed" class="nav-label">{{ item.label }}</span>
          <span v-if="!sidebarCollapsed && item.count !== undefined" class="nav-count">
            {{ item.count }}
          </span>
        </RouterLink>
      </div>
    </nav>

    <div v-if="!sidebarCollapsed" class="sidebar-footer">
      <div class="mode-notice" :class="mode.mode" data-cy="mode-notice">
        <i :class="mode.production ? 'pi pi-server' : 'pi pi-sparkles'" />
        <div v-if="mode.production">
          <strong>Production mode</strong>
          <p>You manage real LDES servers and LDIOs. Changes take effect right away.</p>
        </div>
        <div v-else>
          <strong>Demo mode</strong>
          <p>Everything you see is sample data. Switch to production in the top bar to manage real servers.</p>
        </div>
      </div>
      <div class="links">
        <a v-for="link in links" :key="link.href" :href="link.href" target="_blank" rel="noopener">
          {{ link.label }} <i class="pi pi-external-link" />
        </a>
      </div>
    </div>
    <button class="collapse-toggle" type="button" :aria-label="sidebarCollapsed ? 'Expand menu' : 'Collapse menu'"
      @click="ui.toggleSidebar()">
      <i :class="sidebarCollapsed ? 'pi pi-angle-double-right' : 'pi pi-angle-double-left'" />
    </button>
  </aside>
</template>

<style scoped>
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  width: var(--sidebar-width);
  flex: none;
  display: flex;
  flex-direction: column;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
  color: var(--sidebar-text);
  transition: width 0.2s ease;
  z-index: 10;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: var(--topbar-height);
  padding: 0 20px;
  border-bottom: 1px solid var(--sidebar-border);
  text-decoration: none;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-name {
  color: var(--sidebar-text-strong);
  font-weight: 700;
  font-size: 16px;
  letter-spacing: -0.01em;
}

.brand-tagline {
  font-size: 11.5px;
  color: #7e8ba0;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 16px 12px;
}

.nav-section + .nav-section {
  margin-top: 20px;
}

.nav-section-title {
  padding: 0 10px 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #66748a;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  color: var(--sidebar-text);
  font-weight: 500;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}

.collapsed .nav-item {
  justify-content: center;
}

.nav-item + .nav-item {
  margin-top: 2px;
}

.nav-item .pi {
  font-size: 16px;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: var(--sidebar-text-strong);
  text-decoration: none;
}

.nav-item.active {
  background: rgba(45, 212, 191, 0.12);
  color: #5eead4;
}

.nav-label {
  flex: 1;
}

.nav-count {
  min-width: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  font-size: 11.5px;
  line-height: 20px;
  text-align: center;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--sidebar-border);
}

.mode-notice {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-radius: var(--radius-sm);
  background: rgba(45, 212, 191, 0.08);
  border: 1px solid rgba(45, 212, 191, 0.18);
  font-size: 12px;
}

.mode-notice.demo {
  background: rgba(245, 158, 11, 0.08);
  border-color: rgba(245, 158, 11, 0.2);
}

.mode-notice .pi {
  color: #5eead4;
  margin-top: 2px;
}

.mode-notice.demo .pi {
  color: #fcd34d;
}

.mode-notice strong {
  color: var(--sidebar-text-strong);
}

.mode-notice p {
  margin: 2px 0 0;
  line-height: 1.45;
}

.links {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  padding: 0 4px;
}

.links a {
  color: #7e8ba0;
  font-size: 12px;
}

.links a:hover {
  color: var(--sidebar-text-strong);
}

.links .pi {
  font-size: 9px;
  margin-left: 2px;
}

.collapse-toggle {
  position: absolute;
  right: -12px;
  top: 72px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  color: var(--muted);
  display: grid;
  place-items: center;
  cursor: pointer;
  box-shadow: var(--card-shadow);
}

.collapse-toggle .pi {
  font-size: 10px;
}
</style>
