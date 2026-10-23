<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'
import PipelineChain from '@/components/ldio/PipelineChain.vue'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import { useInstancesStore } from '@/stores/instances'
import { useSandboxStore } from '@/stores/sandbox'

/**
 * Deploys the sandbox pipeline on an LDIO: creates it, or replaces the pipeline with the same name.
 */
const visible = defineModel<boolean>('visible', { required: true })
const router = useRouter()
const toast = useToast()
const sandbox = useSandboxStore()
const instances = useInstancesStore()

const ldioId = ref<string>()
const existing = ref<string[]>([])
const busy = ref(false)
const error = ref<string>()

const options = computed(() => instances.ldios.map((ldio) => ({
  label: `${ldio.name}${ldio.sandbox ? ' (sandbox)' : ''}`,
  value: ldio.id,
})))
const replaces = computed(() => existing.value.includes(sandbox.name))

watch(visible, (open) => {
  if (!open) return
  error.value = undefined
  const origin = sandbox.origin
  ldioId.value = origin.kind === 'ldio' ? origin.ldioId
    : instances.sandboxLdios[0]?.id ?? instances.ldios[0]?.id
})

watch(ldioId, async (id) => {
  existing.value = []
  if (!id) return
  try {
    existing.value = (await api.listPipelines(id)).map((deployed) => deployed.pipeline.name)
  } catch (e) {
    error.value = errorMessage(e)
  }
})

async function deploy() {
  const pipeline = sandbox.conversion.pipeline
  const id = ldioId.value
  if (!pipeline || !id) return
  busy.value = true
  error.value = undefined
  try {
    if (replaces.value) {
      await api.updatePipeline(id, pipeline)
    } else {
      await api.createPipeline(id, pipeline)
    }
    const ldio = instances.find(id)
    sandbox.origin = { kind: 'ldio', ldioId: id, ldioName: ldio?.name ?? '' }
    visible.value = false
    toast.add({
      severity: 'success',
      summary: `${pipeline.name} ${replaces.value ? 'updated' : 'deployed'} on ${ldio?.name}`,
      life: 4000,
    })
    router.push(`/ldios/${id}`)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal header="Deploy pipeline" :style="{ width: '560px' }" data-cy="deploy-dialog">
    <div class="form">
      <div v-if="sandbox.conversion.pipeline" class="summary">
        <div class="mono name">{{ sandbox.name }}</div>
        <PipelineChain :pipeline="sandbox.conversion.pipeline" />
      </div>
      <div class="field">
        <label for="deploy-ldio">LDIO</label>
        <Select v-model="ldioId" input-id="deploy-ldio" :options="options" option-label="label" option-value="value"
          placeholder="Pick an LDIO" fluid data-cy="deploy-ldio" />
      </div>
      <Message v-if="replaces" severity="warn" :closable="false">
        A pipeline named <strong>{{ sandbox.name }}</strong> already runs on this LDIO. It is stopped and
        replaced (LDIO has no in-place update).
      </Message>
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    </div>
    <template #footer>
      <Button label="Cancel" severity="secondary" text @click="visible = false" />
      <Button :label="replaces ? 'Replace pipeline' : 'Deploy'" icon="pi pi-cloud-upload" :loading="busy"
        :disabled="!ldioId" data-cy="deploy-submit" @click="deploy" />
    </template>
  </Dialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--hover-bg);
}

.name {
  font-weight: 600;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-weight: 500;
}
</style>
