import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import type { Sandbox } from '@/domain/sandbox'
import { useInstancesStore } from '@/stores/instances'
import { useModeStore } from '@/stores/mode'

/**
 * The local LDES server and LDIO of the sandbox, shared by the dashboard and the sandbox, so a
 * start that takes minutes keeps going while the user moves on.
 */
export const useSandboxEnvironmentStore = defineStore('sandboxEnvironment', () => {
  const sandbox = ref<Sandbox>()
  const loading = ref(false)
  const starting = ref(false)
  const stopping = ref(false)
  const error = ref<string>()
  /** When the current start began, to show how long it takes */
  const startedAt = ref<number>()

  const state = computed(() => sandbox.value?.state ?? 'STOPPED')
  const busy = computed(() => starting.value || stopping.value)

  async function load() {
    loading.value = true
    try {
      sandbox.value = await api.getSandbox()
      error.value = undefined
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function ensureLoaded() {
    if (!sandbox.value && !loading.value) await load()
  }

  /** @returns whether the sandbox runs afterwards */
  async function start(): Promise<boolean> {
    if (busy.value) return false
    starting.value = true
    startedAt.value = Date.now()
    error.value = undefined
    const stillCurrent = sameMode()
    try {
      const started = await api.startSandbox()
      if (stillCurrent()) sandbox.value = started
      return true
    } catch (e) {
      if (!stillCurrent()) return false
      error.value = errorMessage(e)
      // Part of it may run by now
      await load()
      return false
    } finally {
      starting.value = false
      startedAt.value = undefined
      await useInstancesStore().load().catch(() => undefined)
    }
  }

  /** @returns whether the sandbox stopped */
  async function stop(): Promise<boolean> {
    if (busy.value) return false
    stopping.value = true
    error.value = undefined
    try {
      await api.stopSandbox()
      sandbox.value = await api.getSandbox()
      return true
    } catch (e) {
      error.value = errorMessage(e)
      return false
    } finally {
      stopping.value = false
      await useInstancesStore().load().catch(() => undefined)
    }
  }

  /** Whether the app is still in the mode it was in when this was called, when asked later */
  function sameMode(): () => boolean {
    const mode = useModeStore()
    const generation = mode.generation
    return () => mode.generation === generation
  }

  /** Forgets everything, e.g. after switching between demo and production mode */
  function reset() {
    sandbox.value = undefined
    error.value = undefined
  }

  return {
    sandbox,
    state,
    loading,
    starting,
    stopping,
    busy,
    error,
    startedAt,
    load,
    ensureLoaded,
    start,
    stop,
    reset,
  }
})
