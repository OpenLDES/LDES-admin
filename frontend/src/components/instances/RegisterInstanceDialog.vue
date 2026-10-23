<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
const emit = defineEmits<{ registered: [instance: Instance] }>()

const instances = useInstancesStore()
const name = ref('')
const url = ref('')
const sandbox = ref(false)
const error = ref<string>()
const busy = ref(false)

const label = computed(() => instanceTypeLabel[props.type])
const urlValid = computed(() => /^https?:\/\/[^\s/]+/.test(url.value.trim()))
const valid = computed(() => name.value.trim() !== '' && urlValid.value)
const placeholder = computed(() => props.type === 'LDIO'
  ? 'https://ldio.example.org' : 'https://ldes.example.org')

watch(visible, (open) => {
  if (open) {
    name.value = ''
    url.value = ''
    sandbox.value = false
    error.value = undefined
  }
})

async function submit() {
  if (!valid.value) return
  busy.value = true
  error.value = undefined
  try {
    const instance = await instances.register(props.type, {
      name: name.value.trim(),
      url: url.value.trim(),
      sandbox: sandbox.value,
    })
    visible.value = false
    emit('registered', instance)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal :header="`Add an existing ${label}`" :style="{ width: '480px' }"
    data-cy="register-dialog">
    <form class="form" @submit.prevent="submit">
      <p class="muted intro">
        LDES-admin talks to the {{ label }} through its admin API. It does not start or stop it.
      </p>
      <div class="field">
        <label for="instance-name">Name</label>
        <InputText id="instance-name" v-model="name" placeholder="Production" autofocus fluid
          data-cy="instance-name" />
      </div>
      <div class="field">
        <label for="instance-url">Base URL</label>
        <InputText id="instance-url" v-model="url" :placeholder="placeholder" fluid
          :invalid="url !== '' && !urlValid" data-cy="instance-url" />
        <small class="muted">The admin API is expected at <code>/admin/api/v1</code>, metrics at
          <code>/actuator</code>.</small>
      </div>
      <div class="checkbox">
        <Checkbox v-model="sandbox" input-id="instance-sandbox" binary />
        <label for="instance-sandbox">Use it as a sandbox</label>
      </div>
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <div class="buttons">
        <Button label="Cancel" severity="secondary" text @click="visible = false" />
        <Button type="submit" :label="`Add ${label}`" icon="pi pi-plus" :loading="busy"
          :disabled="!valid" data-cy="register-submit" />
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

.buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
