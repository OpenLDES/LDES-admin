<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Checkbox from 'primevue/checkbox'
import Message from 'primevue/message'
import Select from 'primevue/select'
import ExampleSteps from '@/components/examples/ExampleSteps.vue'
import type { Instance, InstanceType } from '@/domain/instance'
import { instanceTypeLabel } from '@/domain/instance'
import { examples } from '@/examples'
import { failed, type InstallRequest, type InstallStep, installExample, plannedSteps } from '@/examples/install'
import { useInstancesStore } from '@/stores/instances'
import { errorMessage } from '@/composables/useAsync'

/**
 * Starts a local LDES server or LDIO, and creates the demos the user picks on it: an event stream
 * on the new server fed by a pipeline on a local LDIO, or the other way around.
 */
const props = defineProps<{ type: InstanceType }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ started: [instance: Instance] }>()

const instances = useInstancesStore()
const name = ref('')
const sandbox = ref(true)
const busy = ref(false)
const error = ref<string>()
const progress = ref(0)
/** The examples to create, none unless the user picks them */
const chosen = ref<string[]>([])
/** The local instance of the other type that the examples use */
const partnerId = ref<string | null>(null)
const exampleGroups = ref<{ title: string; icon: string; steps: InstallStep[] }[]>([])
/** Started, but not every example could be created: the dialog stays open to tell */
const started = ref<Instance>()
let timer: ReturnType<typeof setInterval> | undefined

const label = computed(() => instanceTypeLabel[props.type])
const isLdio = computed(() => props.type === 'LDIO')
const image = computed(() => isLdio.value
  ? 'openldes/ldi-orchestrator:3.1.1' : 'openldes/ldes-server:4.2.1')
const steps = computed(() => isLdio.value
  ? ['Pulling the LDIO image', 'Starting the container', 'Waiting for the pipeline API']
  : ['Pulling the LDES server image', 'Starting Postgres', 'Starting the LDES server',
    'Waiting until it is healthy'])
/** Local instances share a Docker network, so a local LDIO reaches a local LDES server */
const partners = computed(() => (isLdio.value ? instances.ldesServers : instances.ldios)
  .filter((instance) => instance.origin === 'LOCAL')
  .sort((a, b) => Number(b.sandbox) - Number(a.sandbox)))
const partner = computed(() => partners.value.find((instance) => instance.id === partnerId.value))
const examplesHint = computed(() => {
  if (isLdio.value) {
    return partners.value.length
      ? 'The pipelines run on the new LDIO and ingest in the event streams of a local LDES server.'
      : 'Start a local LDES server first: the demo pipelines ingest in it.'
  }
  if (!partners.value.length) {
    return 'No local LDIO runs, so the demos come without pipelines; the sensor data is ingested directly.'
  }
  return 'The event streams go to the new LDES server, fed by pipelines on a local LDIO.'
})

// The instances may still be loading when the dialog opens
watch(partners, (list) => {
  if (!list.some((instance) => instance.id === partnerId.value)) partnerId.value = list[0]?.id ?? null
})

watch(visible, (open) => {
  if (open) {
    name.value = isLdio.value ? 'Sandbox LDIO 2' : 'Sandbox 2'
    sandbox.value = true
    error.value = undefined
    progress.value = 0
    chosen.value = []
    partnerId.value = partners.value[0]?.id ?? null
    exampleGroups.value = []
    started.value = undefined
  } else if (started.value) {
    emit('started', started.value)
    started.value = undefined
  }
})

async function submit() {
  if (!name.value.trim()) return
  busy.value = true
  error.value = undefined
  progress.value = 0
  exampleGroups.value = []
  timer = setInterval(() => {
    progress.value = Math.min(progress.value + 1, steps.value.length - 1)
  }, 2000 / steps.value.length)
  try {
    const instance = await instances.startLocal(props.type, {
      name: name.value.trim(),
      sandbox: sandbox.value,
    })
    clearInterval(timer)
    progress.value = steps.value.length
    if (await createExamples(instance)) {
      visible.value = false
      emit('started', instance)
    } else {
      started.value = instance
      error.value = 'Not every part of the demo could be created.'
    }
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    clearInterval(timer)
    busy.value = false
  }
}

/** Creates the chosen examples one after the other; false when one of them failed */
async function createExamples(instance: Instance): Promise<boolean> {
  const requests: InstallRequest[] = examples
    .filter((example) => chosen.value.includes(example.id))
    .map((example) => ({
      example,
      server: isLdio.value ? partner.value! : instance,
      ldio: isLdio.value ? instance : partner.value,
      parts: { stream: true, views: true, pipeline: true, data: true },
    }))
  exampleGroups.value = requests.map(({ example, ...request }) =>
    ({ title: example.title, icon: example.icon, steps: plannedSteps({ example, ...request }) }))
  let succeeded = true
  for (const [index, request] of requests.entries()) {
    const result = await installExample(request, {
      onChange: (updated) => { exampleGroups.value[index].steps = updated },
    })
    succeeded &&= !failed(result)
  }
  return succeeded
}

function open() {
  visible.value = false
}

onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="`Start a local ${label}`" :style="{ width: '520px' }"
    :closable="!busy" data-cy="start-local-dialog">
    <form class="form" @submit.prevent="submit">
      <p class="muted intro">
        LDES-admin starts <code>{{ image }}</code> as a Docker container on this machine, and
        removes it again when you stop it.
      </p>
      <div class="field">
        <label for="local-name">Name</label>
        <InputText id="local-name" v-model="name" fluid :disabled="busy || !!started" />
      </div>
      <div class="checkbox">
        <Checkbox v-model="sandbox" input-id="local-sandbox" binary :disabled="busy || !!started" />
        <label for="local-sandbox">Use it as a sandbox</label>
      </div>

      <fieldset class="examples" data-cy="examples">
        <legend>Demos</legend>
        <div v-for="example in examples" :key="example.id" class="checkbox">
          <Checkbox v-model="chosen" :value="example.id" :input-id="`example-${example.id}`"
            :disabled="busy || !!started || (isLdio && !partners.length)" :data-cy="`example-${example.id}`" />
          <label :for="`example-${example.id}`">{{ example.offer }}</label>
        </div>
        <small class="muted">{{ examplesHint }}</small>
        <div v-if="chosen.length && partners.length" class="field">
          <label for="example-partner">{{ isLdio ? 'Create the event streams on' : 'Run the pipelines on' }}</label>
          <Select v-model="partnerId" input-id="example-partner" :options="partners" option-label="name"
            option-value="id" :show-clear="!isLdio" :placeholder="isLdio ? undefined : 'No LDIO: no pipelines'"
            fluid :disabled="busy || !!started" data-cy="example-partner" />
        </div>
      </fieldset>

      <ol v-if="busy || started" class="steps">
        <li v-for="(step, index) in steps" :key="step" :class="{ done: index < progress, current: index === progress }">
          <i :class="index < progress ? 'pi pi-check' : index === progress ? 'pi pi-spin pi-spinner' : 'pi pi-circle'" />
          {{ step }}
        </li>
      </ol>
      <ExampleSteps v-if="exampleGroups.length" :groups="exampleGroups" />
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <div class="buttons">
        <template v-if="started">
          <Button :label="`Open ${started.name}`" icon="pi pi-arrow-right" icon-pos="right" data-cy="open-started"
            @click="open" />
        </template>
        <template v-else>
          <Button label="Cancel" severity="secondary" text :disabled="busy" @click="visible = false" />
          <Button type="submit" :label="busy ? 'Starting…' : `Start ${label}`" icon="pi pi-play"
            :loading="busy" :disabled="!name.trim() || (isLdio && chosen.length > 0 && !partner)"
            data-cy="start-local-submit" />
        </template>
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

.examples {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 12px 14px 14px;
  border: 1px solid var(--card-border);
  border-radius: 8px;
}

.examples legend {
  padding: 0 6px;
  font-weight: 500;
}

.examples small {
  line-height: 1.45;
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
