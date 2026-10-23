<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Checkbox from 'primevue/checkbox'
import Message from 'primevue/message'
import type { Instance, InstanceType } from '@/domain/instance'
import { instanceTypeLabel } from '@/domain/instance'
import { useInstancesStore } from '@/stores/instances'
import { errorMessage } from '@/composables/useAsync'

const props = defineProps<{ type: InstanceType }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ started: [instance: Instance] }>()

const instances = useInstancesStore()
const name = ref('')
const sandbox = ref(true)
const busy = ref(false)
const error = ref<string>()
const progress = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

const label = computed(() => instanceTypeLabel[props.type])
const image = computed(() => props.type === 'LDIO'
  ? 'openldes/ldi-orchestrator:3.1.1' : 'openldes/ldes-server:4.2.1')
const steps = computed(() => props.type === 'LDIO'
  ? ['Pulling the LDIO image', 'Starting the container', 'Waiting for the pipeline API']
  : ['Pulling the LDES server image', 'Starting Postgres', 'Starting the LDES server',
    'Waiting until it is healthy'])

watch(visible, (open) => {
  if (open) {
    name.value = props.type === 'LDIO' ? 'Sandbox LDIO 2' : 'Sandbox 2'
    sandbox.value = true
    error.value = undefined
    progress.value = 0
  }
})

async function submit() {
  if (!name.value.trim()) return
  busy.value = true
  error.value = undefined
  progress.value = 0
  timer = setInterval(() => {
    progress.value = Math.min(progress.value + 1, steps.value.length - 1)
  }, 2000 / steps.value.length)
  try {
    const instance = await instances.startLocal(props.type, {
      name: name.value.trim(),
      sandbox: sandbox.value,
    })
    visible.value = false
    emit('started', instance)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    clearInterval(timer)
    busy.value = false
  }
}

onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="`Start a local ${label}`" :style="{ width: '480px' }"
    :closable="!busy" data-cy="start-local-dialog">
    <form class="form" @submit.prevent="submit">
      <p class="muted intro">
        LDES-admin starts <code>{{ image }}</code> as a Docker container on this machine, and
        removes it again when you stop it.
      </p>
      <div class="field">
        <label for="local-name">Name</label>
        <InputText id="local-name" v-model="name" fluid :disabled="busy" />
      </div>
      <div class="checkbox">
        <Checkbox v-model="sandbox" input-id="local-sandbox" binary :disabled="busy" />
        <label for="local-sandbox">Use it as a sandbox</label>
      </div>
      <ol v-if="busy" class="steps">
        <li v-for="(step, index) in steps" :key="step" :class="{ done: index < progress, current: index === progress }">
          <i :class="index < progress ? 'pi pi-check' : index === progress ? 'pi pi-spin pi-spinner' : 'pi pi-circle'" />
          {{ step }}
        </li>
      </ol>
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <div class="buttons">
        <Button label="Cancel" severity="secondary" text :disabled="busy" @click="visible = false" />
        <Button type="submit" :label="busy ? 'Starting…' : `Start ${label}`" icon="pi pi-play"
          :loading="busy" :disabled="!name.trim()" data-cy="start-local-submit" />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.intro {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-weight: 500;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
}

.steps {
  list-style: none;
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--p-content-hover-background);
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}

.steps li {
  display: flex;
  align-items: center;
  gap: 10px;
}

.steps .pi {
  font-size: 12px;
  width: 14px;
}

.steps .done {
  color: var(--text);
}

.steps .done .pi {
  color: var(--p-green-600);
}

.steps .current {
  color: var(--text);
  font-weight: 500;
}

.buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
