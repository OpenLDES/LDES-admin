import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { type ApiMode, apiMode, backendAvailable, setApiMode } from '@/api'

const STORAGE_KEY = 'ldes-admin.mode'

/**
 * Picks the mode to start in: the one the user chose before, else production when the backend
 * answers, else demo.
 */
export async function initialMode(): Promise<{ mode: ApiMode; backendUp: boolean | undefined }> {
  const stored = storedMode()
  if (stored === 'demo') return { mode: 'demo', backendUp: undefined }
  const backendUp = await backendAvailable()
  return { mode: stored ?? (backendUp ? 'production' : 'demo'), backendUp }
}

function storedMode(): ApiMode | undefined {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null
  return stored === 'demo' || stored === 'production' ? stored : undefined
}

/**
 * Demo or production mode. Switching changes the implementation of the API and starts the app
 * afresh: the views are rebuilt and the shared stores reloaded.
 */
export const useModeStore = defineStore('mode', () => {
  const mode = ref<ApiMode>(apiMode())
  /** Whether the backend answered when it was last checked; undefined if it was not checked */
  const backendUp = ref<boolean>()
  /** Changes on every switch, so views that depend on the API can be rebuilt */
  const generation = ref(0)

  const production = computed(() => mode.value === 'production')
  const backendUnreachable = computed(() => production.value && backendUp.value === false)

  function start(initial: ApiMode, up: boolean | undefined) {
    setApiMode(initial)
    mode.value = initial
    backendUp.value = up
  }

  async function switchTo(next: ApiMode) {
    if (next === mode.value) return
    localStorage.setItem(STORAGE_KEY, next)
    setApiMode(next)
    mode.value = next
    generation.value += 1
    if (next === 'production') backendUp.value = await backendAvailable()
  }

  async function checkBackend() {
    backendUp.value = await backendAvailable()
    return backendUp.value
  }

  return { mode, backendUp, generation, production, backendUnreachable, start, switchTo, checkBackend }
})
