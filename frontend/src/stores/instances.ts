import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api'
import type {
  Instance,
  InstanceType,
  RegisterInstanceRequest,
  StartLocalInstanceRequest,
} from '@/domain/instance'

/**
 * The LDES servers and LDIOs, shared by the navigation, the lists and the dialogs.
 */
export const useInstancesStore = defineStore('instances', () => {
  const ldesServers = ref<Instance[]>([])
  const ldios = ref<Instance[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  const sandboxServers = computed(() => ldesServers.value.filter((server) => server.sandbox))
  const sandboxLdios = computed(() => ldios.value.filter((ldio) => ldio.sandbox))
  /** Counts loads: one that is overtaken, e.g. by a switch of mode, must not overwrite the next */
  let loads = 0

  function listOf(type: InstanceType) {
    return type === 'LDES_SERVER' ? ldesServers : ldios
  }

  async function load() {
    const current = ++loads
    loading.value = true
    try {
      const [servers, orchestrators] = await Promise.all([
        api.listInstances('LDES_SERVER'),
        api.listInstances('LDIO'),
      ])
      if (current !== loads) return
      ldesServers.value = servers
      ldios.value = orchestrators
      loaded.value = true
    } finally {
      if (current === loads) loading.value = false
    }
  }

  async function ensureLoaded() {
    if (!loaded.value && !loading.value) await load()
  }

  function find(id: string): Instance | undefined {
    return [...ldesServers.value, ...ldios.value].find((instance) => instance.id === id)
  }

  async function register(type: InstanceType, request: RegisterInstanceRequest) {
    const instance = await api.registerInstance(type, request)
    insert(instance)
    return instance
  }

  async function startLocal(type: InstanceType, request: StartLocalInstanceRequest) {
    const instance = await api.startLocalInstance(type, request)
    insert(instance)
    return instance
  }

  async function remove(instance: Instance) {
    await api.unregisterInstance(instance.type, instance.id)
    const list = listOf(instance.type)
    list.value = list.value.filter((candidate) => candidate.id !== instance.id)
  }

  function insert(instance: Instance) {
    const list = listOf(instance.type)
    list.value = [...list.value, instance].sort((a, b) => a.name.localeCompare(b.name))
  }

  return {
    ldesServers,
    ldios,
    loaded,
    loading,
    sandboxServers,
    sandboxLdios,
    load,
    ensureLoaded,
    find,
    register,
    startLocal,
    remove,
  }
})
