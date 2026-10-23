<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Checkbox from 'primevue/checkbox'
import Message from 'primevue/message'
import CodeEditor from '@/components/common/CodeEditor.vue'
import { api } from '@/api'
import { errorMessage } from '@/composables/useAsync'
import type { EventStream } from '@/domain/ldes'

const props = defineProps<{ serverId: string; serverUrl: string }>()
const visible = defineModel<boolean>('visible', { required: true })
const emit = defineEmits<{ created: [stream: EventStream] }>()

const collection = ref('my-collection')
const timestampPath = ref('dcterms:created')
const createVersions = ref(false)
const definition = ref('')
const edited = ref(false)
const busy = ref(false)
const error = ref<string>()

const timestampPaths = [
  { label: 'dcterms:created', value: 'dcterms:created' },
  { label: 'dcterms:modified', value: 'dcterms:modified' },
  { label: 'prov:generatedAtTime', value: 'prov:generatedAtTime' },
  { label: 'sosa:resultTime', value: 'sosa:resultTime' },
]

const generated = computed(() => `@prefix ldes:    <https://w3id.org/ldes#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix prov:    <http://www.w3.org/ns/prov#> .
@prefix sosa:    <http://www.w3.org/ns/sosa/> .
@prefix tree:    <https://w3id.org/tree#> .
@prefix sh:      <http://www.w3.org/ns/shacl#> .
@prefix server:  <${props.serverUrl}/> .

server:${collection.value} a ldes:EventStream ;
    ldes:timestampPath ${timestampPath.value} ;
    ldes:versionOfPath dcterms:isVersionOf ;${createVersions.value ? '\n    ldes:createVersions true ;' : ''}
    tree:shape server:${collection.value}-shape .

server:${collection.value}-shape a sh:NodeShape .
`)

watch(generated, (value) => {
  if (!edited.value) definition.value = value
}, { immediate: true })

watch(visible, (open) => {
  if (open) {
    collection.value = 'my-collection'
    createVersions.value = false
    edited.value = false
    definition.value = generated.value
    error.value = undefined
  }
})

function onEdit(value: string) {
  definition.value = value
  edited.value = value !== generated.value
}

async function submit() {
  busy.value = true
  error.value = undefined
  try {
    const stream = await api.createEventStream(props.serverId, definition.value, 'text/turtle')
    visible.value = false
    emit('created', stream)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal header="New event stream" :style="{ width: '760px' }"
    data-cy="create-stream-dialog">
    <div class="form">
      <div class="fields">
        <div class="field">
          <label for="stream-collection">Collection name</label>
          <InputText id="stream-collection" v-model="collection" :disabled="edited" data-cy="stream-collection" />
        </div>
        <div class="field">
          <label for="stream-timestamp">Timestamp path</label>
          <Select v-model="timestampPath" input-id="stream-timestamp" :options="timestampPaths"
            option-label="label" option-value="value" :disabled="edited" />
        </div>
        <div class="checkbox">
          <Checkbox v-model="createVersions" input-id="stream-versions" binary :disabled="edited" />
          <label for="stream-versions">Ingest state objects (the server creates versions)</label>
        </div>
      </div>
      <div class="row">
        <span class="label">Definition</span>
        <span class="spacer" />
        <span v-if="edited" class="muted small">Edited by hand: the fields above no longer apply</span>
        <Button v-if="edited" label="Reset" text size="small" @click="onEdit(generated); edited = false" />
      </div>
      <CodeEditor :model-value="definition" language="turtle" min-height="280px" max-height="420px"
        @update:model-value="onEdit" />
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    </div>
    <template #footer>
      <Button label="Cancel" severity="secondary" text @click="visible = false" />
      <Button label="Create event stream" icon="pi pi-plus" :loading="busy" data-cy="create-stream-submit"
        @click="submit" />
    </template>
  </Dialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
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
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
