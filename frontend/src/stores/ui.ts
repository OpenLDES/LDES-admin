import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'ldes-admin.dark'
const PROCESS_REFRESH_KEY = 'ldes-admin.process-refresh'

/** How often the background processes of a server refresh, in seconds, unless the user chose otherwise */
export const DEFAULT_PROCESS_REFRESH_SECONDS = 2

/**
 * Presentation preferences: dark mode, the collapsed sidebar, and how often the background
 * processes refresh (0: never).
 */
export const useUiStore = defineStore('ui', () => {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null
  const prefersDark = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-color-scheme: dark)').matches
  const dark = ref(stored === null ? prefersDark : stored === 'true')
  const sidebarCollapsed = ref(false)
  const storedRefresh = Number(typeof localStorage !== 'undefined'
    ? localStorage.getItem(PROCESS_REFRESH_KEY) ?? NaN : NaN)
  const processRefreshSeconds = ref(Number.isFinite(storedRefresh) && storedRefresh >= 0
    ? storedRefresh : DEFAULT_PROCESS_REFRESH_SECONDS)

  watch(dark, (value) => {
    document.documentElement.classList.toggle('app-dark', value)
    localStorage.setItem(STORAGE_KEY, String(value))
  }, { immediate: true })

  watch(processRefreshSeconds, (value) => localStorage.setItem(PROCESS_REFRESH_KEY, String(value)))

  function toggleDark() {
    dark.value = !dark.value
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { dark, sidebarCollapsed, processRefreshSeconds, toggleDark, toggleSidebar }
})
