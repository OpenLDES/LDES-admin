import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'ldes-admin.dark'

/**
 * Presentation preferences: dark mode and the collapsed sidebar.
 */
export const useUiStore = defineStore('ui', () => {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null
  const prefersDark = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-color-scheme: dark)').matches
  const dark = ref(stored === null ? prefersDark : stored === 'true')
  const sidebarCollapsed = ref(false)

  watch(dark, (value) => {
    document.documentElement.classList.toggle('app-dark', value)
    localStorage.setItem(STORAGE_KEY, String(value))
  }, { immediate: true })

  function toggleDark() {
    dark.value = !dark.value
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { dark, sidebarCollapsed, toggleDark, toggleSidebar }
})
