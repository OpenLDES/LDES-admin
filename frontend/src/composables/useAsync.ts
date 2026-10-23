import { onMounted, ref, shallowRef, watch, type Ref, type WatchSource } from 'vue'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '@/api'

/**
 * Loads data from the API and keeps track of its loading and error state. While reloading, the
 * previous data stays visible.
 */
export function useAsync<T>(loader: () => Promise<T>, options: { watch?: WatchSource[] } = {}) {
  const data = shallowRef<T>()
  const loading = ref(false)
  const error = ref<string>()

  async function reload() {
    loading.value = true
    error.value = undefined
    try {
      data.value = await loader()
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  onMounted(reload)
  if (options.watch) watch(options.watch, reload)

  return { data: data as Ref<T | undefined>, loading, error, reload }
}

/**
 * Runs an action against the API and reports the outcome in a toast.
 */
export function useAction() {
  const toast = useToast()
  const busy = ref(false)

  async function run<T>(action: () => Promise<T>, success?: string | ((result: T) => string)):
    Promise<T | undefined> {
    busy.value = true
    try {
      const result = await action()
      if (success) {
        toast.add({
          severity: 'success',
          summary: typeof success === 'function' ? success(result) : success,
          life: 3500,
        })
      }
      return result
    } catch (e) {
      toast.add({ severity: 'error', summary: 'That did not work', detail: errorMessage(e), life: 7000 })
      return undefined
    } finally {
      busy.value = false
    }
  }

  return { run, busy }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.detail
  return error instanceof Error ? error.message : String(error)
}
